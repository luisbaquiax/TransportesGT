package org.transportsgt.tripservice.services.viaje.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.payload.viajes.EstadoViaje;
import org.transportsgt.tripservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.tripservice.dto.request.Requests;
import org.transportsgt.tripservice.dto.response.Responses;
import org.transportsgt.tripservice.eventos.EventosViaje;
import org.transportsgt.tripservice.eventos.RecursosEvento;
import org.transportsgt.tripservice.exception.ReglaNegocioException;
import org.transportsgt.tripservice.exception.SolicitudInvalidaException;
import org.transportsgt.tripservice.mappers.EnsambladorViajes;
import org.transportsgt.tripservice.models.BusRef;
import org.transportsgt.tripservice.models.ChoferRef;
import org.transportsgt.tripservice.models.RegistroLlegada;
import org.transportsgt.tripservice.models.RegistroSalida;
import org.transportsgt.tripservice.models.Viaje;
import org.transportsgt.tripservice.repositories.BusRefRepository;
import org.transportsgt.tripservice.repositories.ChoferRefRepository;
import org.transportsgt.tripservice.repositories.RegistroLlegadaRepository;
import org.transportsgt.tripservice.repositories.RegistroSalidaRepository;
import org.transportsgt.tripservice.services.autorizacion.AutorizacionViaje;
import org.transportsgt.tripservice.services.comun.BuscadorViajes;
import org.transportsgt.tripservice.services.viaje.RegistroViajeService;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistroViajeServiceImpl implements RegistroViajeService {

    private final BuscadorViajes buscador;
    private final AutorizacionViaje autorizacion;
    private final RegistroSalidaRepository registroSalidaRepository;
    private final RegistroLlegadaRepository registroLlegadaRepository;
    private final ChoferRefRepository choferRefRepository;
    private final BusRefRepository busRefRepository;
    private final GuardadoViaje guardado;
    private final RecursosEvento recursos;
    private final EventosViaje eventos;
    private final EnsambladorViajes ensamblador;

    @Override
    @Transactional
    public Responses.ViajeResponse registrarSalida(UUID id, Requests.SalidaRequest request,
                                                   UsuarioAutenticado usuario) {
        Viaje v = buscador.cargar(id);
        autorizacion.verificarSalidaLlegada(v, usuario);
        if (v.getEstado() != EstadoViaje.PROGRAMADO) {
            throw new ReglaNegocioException("ESTADO_INVALIDO",
                    "Solo se puede registrar la salida de un viaje PROGRAMADO");
        }
        if (registroSalidaRepository.existsByViajeId(id)) {
            throw new ReglaNegocioException("SALIDA_YA_REGISTRADA", "La salida ya fue registrada");
        }
        registroSalidaRepository.save(RegistroSalida.builder()
                .viaje(v)
                .idBus(v.getIdBus())
                .idChofer(v.getIdChofer())
                .fechaHoraSalidaReal(request.fechaHoraSalidaReal())
                .kilometrajeInicial(request.kilometrajeInicial())
                .registradoPor(usuario.id())
                .build());
        v.setEstado(EstadoViaje.EN_TRANSITO);
        v = guardado.guardar(v);
        eventos.salidaRegistrada(v, request.fechaHoraSalidaReal(), request.kilometrajeInicial(), usuario.id());
        // Catálogo §2.3: el cambio de estado (EN_TRANSITO) también se publica para Boletos, Alquileres y Reportes
        eventos.viajeActualizado(v, recursos.bus(v.getIdBus()), recursos.chofer(v.getIdChofer()));
        log.info("Salida del viaje {} registrada por {}", id, usuario.id());
        return ensamblador.respuesta(v);
    }

    @Override
    @Transactional
    public Responses.ViajeResponse registrarLlegada(UUID id, Requests.LlegadaRequest request,
                                                    UsuarioAutenticado usuario) {
        Viaje v = buscador.cargar(id);
        autorizacion.verificarSalidaLlegada(v, usuario);
        if (v.getEstado() != EstadoViaje.EN_TRANSITO) {
            throw new ReglaNegocioException("ESTADO_INVALIDO",
                    "Solo se puede registrar la llegada de un viaje EN_TRANSITO");
        }
        if (registroLlegadaRepository.existsByViajeId(id)) {
            throw new ReglaNegocioException("LLEGADA_YA_REGISTRADA", "La llegada ya fue registrada");
        }
        RegistroSalida salida = registroSalidaRepository.findByViajeId(id)
                .orElseThrow(() -> new ReglaNegocioException("SALIDA_NO_REGISTRADA",
                        "No se puede registrar la llegada sin haber registrado la salida"));
        if (request.kilometrajeFinal().compareTo(salida.getKilometrajeInicial()) < 0) {
            throw new SolicitudInvalidaException("El kilometraje final no puede ser menor que el inicial");
        }
        BigDecimal distancia = request.kilometrajeFinal().subtract(salida.getKilometrajeInicial());
        ChoferRef chofer = choferRefRepository.findById(v.getIdChofer()).orElse(null);
        BusRef bus = busRefRepository.findById(v.getIdBus()).orElse(null);
        // Instantáneas al momento de la llegada (las usa Costos sin consultar a Viajes)
        BigDecimal salarioBase = chofer != null ? chofer.getSalarioBaseViaje() : BigDecimal.ZERO;
        UUID idSucursalBus = bus != null ? bus.getIdSucursal() : v.getIdSucursalOrigen();

        registroLlegadaRepository.save(RegistroLlegada.builder()
                .viaje(v)
                .fechaHoraLlegadaReal(request.fechaHoraLlegadaReal())
                .kilometrajeFinal(request.kilometrajeFinal())
                .distanciaRecorridaKm(distancia)
                .gastoCombustibleTotal(request.gastoCombustibleTotal())
                .salarioBaseChofer(salarioBase)
                .registradoPor(usuario.id())
                .build());
        v.setEstado(EstadoViaje.FINALIZADO);
        v = guardado.guardar(v);
        eventos.llegadaRegistrada(v, idSucursalBus, salarioBase,
                salida.getFechaHoraSalidaReal(), request.fechaHoraLlegadaReal(),
                salida.getKilometrajeInicial(), request.kilometrajeFinal(),
                distancia, request.gastoCombustibleTotal(), usuario.id());
        // Catálogo §2.3: el cambio de estado (FINALIZADO) también se publica
        eventos.viajeActualizado(v, recursos.bus(v.getIdBus()), recursos.chofer(v.getIdChofer()));
        log.info("Llegada del viaje {} registrada; viaje FINALIZADO", id);
        return ensamblador.respuesta(v);
    }
}
