package org.transportsgt.tripservice.services.viaje.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.payload.viajes.EstadoViaje;
import org.transportsgt.eventos.payload.viajes.TipoViaje;
import org.transportsgt.tripservice.clients.BoletosClient;
import org.transportsgt.tripservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.tripservice.dto.request.Requests;
import org.transportsgt.tripservice.dto.response.Responses;
import org.transportsgt.tripservice.eventos.EventosViaje;
import org.transportsgt.tripservice.eventos.RecursosEvento;
import org.transportsgt.tripservice.exception.ReglaNegocioException;
import org.transportsgt.tripservice.mappers.EnsambladorViajes;
import org.transportsgt.tripservice.models.BusRef;
import org.transportsgt.tripservice.models.Ruta;
import org.transportsgt.tripservice.models.SucursalRef;
import org.transportsgt.tripservice.models.Viaje;
import org.transportsgt.tripservice.repositories.BusRefRepository;
import org.transportsgt.tripservice.repositories.RutaRepository;
import org.transportsgt.tripservice.repositories.SucursalRefRepository;
import org.transportsgt.tripservice.repositories.ViajeRepository;
import org.transportsgt.tripservice.services.autorizacion.AutorizacionViaje;
import org.transportsgt.tripservice.services.comun.BuscadorViajes;
import org.transportsgt.tripservice.services.viaje.ProgramacionViajesService;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramacionViajesServiceImpl implements ProgramacionViajesService {

    private final ViajeRepository viajeRepository;
    private final RutaRepository rutaRepository;
    private final BusRefRepository busRefRepository;
    private final SucursalRefRepository sucursalRefRepository;
    private final BuscadorViajes buscador;
    private final AutorizacionViaje autorizacion;
    private final ValidadorRecursos validador;
    private final GuardadoViaje guardado;
    private final BoletosClient boletosClient;
    private final RecursosEvento recursos;
    private final EventosViaje eventos;
    private final EnsambladorViajes ensamblador;

    @Override
    @Transactional
    public Responses.ViajeResponse crearRegular(Requests.ViajeRegularRequest request, UsuarioAutenticado usuario) {
        Ruta ruta = rutaRepository.findById(request.idRuta())
                .orElseThrow(() -> new ReglaNegocioException("RUTA_NO_ENCONTRADA",
                        "La ruta " + request.idRuta() + " no existe"));
        autorizacion.verificarProgramacion(ruta, usuario);
        validador.validarHorario(request.fechaHoraSalida(), request.fechaHoraLlegadaEstimada());
        validador.validar(request.idBus(), request.idChofer(), ruta.getIdSucursalOrigen(),
                request.fechaHoraLlegadaEstimada(), null);

        Viaje viaje = Viaje.builder()
                .tipoViaje(TipoViaje.REGULAR)
                .estado(EstadoViaje.PROGRAMADO)
                .ruta(ruta)
                .idSucursalOrigen(ruta.getIdSucursalOrigen())
                .nombreOrigen(nombreSucursal(ruta.getIdSucursalOrigen()))
                .nombreDestino(nombreSucursal(ruta.getIdSucursalDestino()))
                .idBus(request.idBus())
                .idChofer(request.idChofer())
                .fechaHoraSalida(request.fechaHoraSalida())
                .fechaHoraLlegadaEstimada(request.fechaHoraLlegadaEstimada())
                .precioBoleto(ruta.getPrecioBoleto())
                .creadoPor(usuario.id())
                .build();
        viaje = guardado.guardar(viaje);
        // Catálogo §2.3: un viaje nuevo se anuncia con ViajeProgramado (los cambios posteriores, con ViajeActualizado)
        eventos.viajeProgramado(viaje, recursos.bus(viaje.getIdBus()), recursos.chofer(viaje.getIdChofer()));
        return ensamblador.respuesta(viaje);
    }

    @Override
    @Transactional
    public Responses.ViajeResponse actualizar(UUID id, Requests.ViajeUpdateRequest request,
                                              UsuarioAutenticado usuario) {
        Viaje v = buscador.cargar(id);
        autorizacion.verificarEscritura(v, usuario);
        if (v.getEstado() == EstadoViaje.EN_TRANSITO || v.getEstado() == EstadoViaje.FINALIZADO) {
            throw new ReglaNegocioException("VIAJE_NO_MODIFICABLE",
                    "No se puede modificar un viaje que ya inició o finalizó");
        }
        UUID idBusNuevo = request.idBus() != null ? request.idBus() : v.getIdBus();
        UUID idChoferNuevo = request.idChofer() != null ? request.idChofer() : v.getIdChofer();
        OffsetDateTime salida = request.fechaHoraSalida() != null ? request.fechaHoraSalida() : v.getFechaHoraSalida();
        OffsetDateTime llegada = request.fechaHoraLlegadaEstimada() != null
                ? request.fechaHoraLlegadaEstimada() : v.getFechaHoraLlegadaEstimada();
        validador.validarHorario(salida, llegada);
        boolean cambiaBus = idBusNuevo != null && !idBusNuevo.equals(v.getIdBus());
        validador.validar(idBusNuevo, idChoferNuevo, v.getIdSucursalOrigen(), llegada, v.getNumeroPasajeros());
        if (cambiaBus && v.getTipoViaje() == TipoViaje.REGULAR) {
            verificarCapacidadParaBoletosVendidos(v, idBusNuevo);
        }
        v.setIdBus(idBusNuevo);
        v.setIdChofer(idChoferNuevo);
        v.setFechaHoraSalida(salida);
        v.setFechaHoraLlegadaEstimada(llegada);
        v = guardado.guardar(v);
        eventos.viajeActualizado(v, recursos.bus(v.getIdBus()), recursos.chofer(v.getIdChofer()));
        return ensamblador.respuesta(v);
    }

    @Override
    @Transactional
    public Responses.ViajeResponse asignar(UUID id, Requests.AsignacionRequest request, UsuarioAutenticado usuario) {
        Viaje v = buscador.cargar(id);
        autorizacion.verificarEscritura(v, usuario);
        if (v.getTipoViaje() != TipoViaje.ALQUILER_PRIVADO) {
            throw new ReglaNegocioException("TIPO_VIAJE_INVALIDO",
                    "La asignación de recursos es solo para viajes de alquiler");
        }
        if (v.getEstado() != EstadoViaje.PENDIENTE_ASIGNACION) {
            throw new ReglaNegocioException("ESTADO_INVALIDO", "El viaje no está pendiente de asignación");
        }
        validador.validar(request.idBus(), request.idChofer(), v.getIdSucursalOrigen(),
                v.getFechaHoraLlegadaEstimada(), v.getNumeroPasajeros());
        v.setIdBus(request.idBus());
        v.setIdChofer(request.idChofer());
        v.setEstado(EstadoViaje.PROGRAMADO);
        v = guardado.guardar(v);
        eventos.viajeActualizado(v, recursos.bus(v.getIdBus()), recursos.chofer(v.getIdChofer()));
        return ensamblador.respuesta(v);
    }

    @Override
    @Transactional
    public void eliminar(UUID id, UsuarioAutenticado usuario) {
        Viaje v = buscador.cargar(id);
        autorizacion.verificarEscritura(v, usuario);
        if (v.getEstado() == EstadoViaje.EN_TRANSITO || v.getEstado() == EstadoViaje.FINALIZADO) {
            throw new ReglaNegocioException("VIAJE_NO_ELIMINABLE",
                    "El viaje ya inició o finalizó y no se puede eliminar");
        }
        Viaje copia = Viaje.builder()
                .id(v.getId()).tipoViaje(v.getTipoViaje()).idAlquiler(v.getIdAlquiler())
                .estado(v.getEstado()).build();
        viajeRepository.delete(v);
        eventos.viajeEliminado(copia, usuario.id());
        log.info("Viaje {} eliminado por {}", id, usuario.id());
    }

    /** Falla cerrada: si Boletos no responde, BoletosClient lanza 503 y el cambio se rechaza. */
    private void verificarCapacidadParaBoletosVendidos(Viaje v, UUID idBusNuevo) {
        int asientoMaximo = boletosClient.asientoMaximo(v.getId());
        BusRef bus = busRefRepository.findById(idBusNuevo).orElse(null);
        if (bus != null && bus.getCapacidadPasajeros() < asientoMaximo) {
            throw new ReglaNegocioException("CAPACIDAD_INSUFICIENTE",
                    "El bus tiene capacidad " + bus.getCapacidadPasajeros()
                            + " pero el viaje ya tiene boletos hasta el asiento " + asientoMaximo);
        }
    }

    private String nombreSucursal(UUID idSucursal) {
        return sucursalRefRepository.findById(idSucursal).map(SucursalRef::getNombre).orElse("Origen");
    }
}
