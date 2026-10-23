package org.transportsgt.tripservice.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.payload.BusAsignado;
import org.transportsgt.eventos.payload.ChoferAsignado;
import org.transportsgt.eventos.payload.EstadoOperativo;
import org.transportsgt.eventos.payload.EstadoViaje;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.eventos.payload.TipoViaje;
import org.transportsgt.tripservice.clients.BoletosClient;
import org.transportsgt.tripservice.config.security.UsuarioAutenticado;
import org.transportsgt.tripservice.dto.request.Requests;
import org.transportsgt.tripservice.dto.response.Responses;
import org.transportsgt.tripservice.eventos.EventosViaje;
import org.transportsgt.tripservice.exception.OperacionNoAutorizadaException;
import org.transportsgt.tripservice.exception.ReglaNegocioException;
import org.transportsgt.tripservice.exception.ServicioNoDisponibleException;
import org.transportsgt.tripservice.exception.SolicitudInvalidaException;
import org.transportsgt.tripservice.exception.ViajeNoEncontradoException;
import org.transportsgt.tripservice.models.BusRef;
import org.transportsgt.tripservice.models.ChoferRef;
import org.transportsgt.tripservice.models.RegistroLlegada;
import org.transportsgt.tripservice.models.RegistroSalida;
import org.transportsgt.tripservice.models.Ruta;
import org.transportsgt.tripservice.models.Viaje;
import org.transportsgt.tripservice.repositories.BusRefRepository;
import org.transportsgt.tripservice.repositories.ChoferRefRepository;
import org.transportsgt.tripservice.repositories.RegistroLlegadaRepository;
import org.transportsgt.tripservice.repositories.RegistroSalidaRepository;
import org.transportsgt.tripservice.repositories.RutaRepository;
import org.transportsgt.tripservice.repositories.SucursalRefRepository;
import org.transportsgt.tripservice.repositories.ViajeRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.transportsgt.tripservice.models.SucursalRef;

/**
 * Ciclo de vida del viaje: creación (regular), asignación de alquiler, actualización, eliminación,
 * salida y llegada. Los registros de salida y llegada son inmutables.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ViajeService {

    /** Máximo de ids por consulta de resumen: coincide con el tamaño máximo de página del historial de Boletos. */
    public static final int MAX_IDS_RESUMEN = 100;

    private static final EnumSet<EstadoViaje> ESTADOS_ACTIVOS =
            EnumSet.of(EstadoViaje.PROGRAMADO, EstadoViaje.EN_TRANSITO);

    private final ViajeRepository viajeRepository;
    private final RutaRepository rutaRepository;
    private final BusRefRepository busRefRepository;
    private final ChoferRefRepository choferRefRepository;
    private final SucursalRefRepository sucursalRefRepository;
    private final RegistroSalidaRepository registroSalidaRepository;
    private final RegistroLlegadaRepository registroLlegadaRepository;
    private final BoletosClient boletosClient;
    private final EventosViaje eventos;

    // ------------------------------------------------------------------
    // Listado y detalle
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Responses.ViajeResponse> listar(Optional<TipoViaje> tipoViaje, Optional<EstadoViaje> estado,
                                                UUID idRuta, UUID idSucursal, OffsetDateTime desde,
                                                OffsetDateTime hasta, Optional<Boolean> sinAsignar,
                                                UsuarioAutenticado usuario) {
        List<Viaje> viajes = viajeRepository.findAll().stream()
                .filter(v -> tipoViaje.isEmpty() || v.getTipoViaje() == tipoViaje.get())
                .filter(v -> estado.isEmpty() || v.getEstado() == estado.get())
                .filter(v -> idRuta == null || (v.getRuta() != null && v.getRuta().getId().equals(idRuta)))
                .filter(v -> idSucursal == null || v.getIdSucursalOrigen().equals(idSucursal))
                .filter(v -> desde == null || !v.getFechaHoraSalida().isBefore(desde))
                .filter(v -> hasta == null || !v.getFechaHoraSalida().isAfter(hasta))
                .filter(v -> sinAsignar.isEmpty() || !sinAsignar.get()
                        || v.getEstado() == EstadoViaje.PENDIENTE_ASIGNACION)
                .filter(v -> puedeVer(v, usuario))
                .sorted((a, b) -> a.getFechaHoraSalida().compareTo(b.getFechaHoraSalida()))
                .toList();
        return viajes.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Responses.ViajeResponse detalle(UUID id, UsuarioAutenticado usuario) {
        Viaje v = cargar(id);
        autorizarLectura(v, usuario);
        return toResponse(v);
    }

    /**
     * Resumen público de varios viajes (historial de compras de cualquier usuario). Los ids que no existen, o cuyo
     * viaje se eliminó, se omiten. Sin restricción de sucursal: solo expone datos que ya son públicos al vender.
     */
    @Transactional(readOnly = true)
    public List<Responses.ViajeResumenResponse> resumen(List<UUID> ids) {
        Set<UUID> unicos = ids == null ? Set.of() : new LinkedHashSet<>(ids);
        unicos.remove(null);
        if (unicos.isEmpty()) {
            throw new SolicitudInvalidaException("Indique al menos un id de viaje");
        }
        if (unicos.size() > MAX_IDS_RESUMEN) {
            throw new SolicitudInvalidaException("Máximo " + MAX_IDS_RESUMEN + " ids por consulta");
        }
        List<Viaje> viajes = viajeRepository.findAllConRutaByIdInOrderByFechaHoraSalida(unicos);
        Set<UUID> idsBuses = viajes.stream().map(Viaje::getIdBus).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> placas = busRefRepository.findAllById(idsBuses).stream()
                .collect(Collectors.toMap(BusRef::getId, BusRef::getPlaca));
        return viajes.stream()
                .map(v -> new Responses.ViajeResumenResponse(
                        v.getId(), v.getTipoViaje(), v.getEstado(),
                        v.getRuta() == null ? null : v.getRuta().getId(),
                        v.getIdSucursalOrigen(), v.getNombreOrigen(), v.getNombreDestino(),
                        v.getFechaHoraSalida(), v.getFechaHoraLlegadaEstimada(), v.getPrecioBoleto(),
                        v.getIdBus() == null ? null : placas.get(v.getIdBus())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Responses.ViajeResponse> activosPorBus(UUID idBus) {
        return viajeRepository.findAllByIdBusAndEstadoInOrderByFechaHoraSalida(idBus, ESTADOS_ACTIVOS)
                .stream().map(this::toResponse).toList();
    }

    // ------------------------------------------------------------------
    // Crear viaje regular
    // ------------------------------------------------------------------

    @Transactional
    public Responses.ViajeResponse crearRegular(Requests.ViajeRegularRequest request, UsuarioAutenticado usuario) {
        Ruta ruta = rutaRepository.findById(request.idRuta())
                .orElseThrow(() -> new ReglaNegocioException("RUTA_NO_ENCONTRADA",
                        "La ruta " + request.idRuta() + " no existe"));
        if (!usuario.perteneceASucursal(ruta.getIdSucursalOrigen())) {
            throw new OperacionNoAutorizadaException(
                    "Solo el administrador de la sucursal de origen puede programar viajes en la ruta");
        }
        if (!request.fechaHoraLlegadaEstimada().isAfter(request.fechaHoraSalida())) {
            throw new SolicitudInvalidaException("La llegada estimada debe ser posterior a la salida");
        }
        validarRecursos(request.idBus(), request.idChofer(), ruta.getIdSucursalOrigen(),
                request.fechaHoraLlegadaEstimada(), null);

        Viaje viaje = Viaje.builder()
                .tipoViaje(TipoViaje.REGULAR)
                .estado(EstadoViaje.PROGRAMADO)
                .ruta(ruta)
                .idSucursalOrigen(ruta.getIdSucursalOrigen())
                .nombreOrigen(sucursalNombre(ruta.getIdSucursalOrigen()))
                .nombreDestino(sucursalNombre(ruta.getIdSucursalDestino()))
                .idBus(request.idBus())
                .idChofer(request.idChofer())
                .fechaHoraSalida(request.fechaHoraSalida())
                .fechaHoraLlegadaEstimada(request.fechaHoraLlegadaEstimada())
                .precioBoleto(ruta.getPrecioBoleto())
                .creadoPor(usuario.id())
                .build();
        viaje = guardarConTraduccionTraslape(viaje);
        // Catálogo §2.3: un viaje nuevo se anuncia con ViajeProgramado (los cambios posteriores, con ViajeActualizado)
        eventos.viajeProgramado(viaje, busAsignado(viaje.getIdBus()), choferAsignado(viaje.getIdChofer()));
        return toResponse(viaje);
    }

    // ------------------------------------------------------------------
    // Actualizar (bus, chofer o fechas; no el tipo)
    // ------------------------------------------------------------------

    @Transactional
    public Responses.ViajeResponse actualizar(UUID id, Requests.ViajeUpdateRequest request,
                                              UsuarioAutenticado usuario) {
        Viaje v = cargar(id);
        autorizarEscritura(v, usuario);
        if (v.getEstado() == EstadoViaje.EN_TRANSITO || v.getEstado() == EstadoViaje.FINALIZADO) {
            throw new ReglaNegocioException("VIAJE_NO_MODIFICABLE",
                    "No se puede modificar un viaje que ya inició o finalizó");
        }
        UUID idBusNuevo = request.idBus() != null ? request.idBus() : v.getIdBus();
        UUID idChoferNuevo = request.idChofer() != null ? request.idChofer() : v.getIdChofer();
        OffsetDateTime salida = request.fechaHoraSalida() != null ? request.fechaHoraSalida() : v.getFechaHoraSalida();
        OffsetDateTime llegada = request.fechaHoraLlegadaEstimada() != null
                ? request.fechaHoraLlegadaEstimada() : v.getFechaHoraLlegadaEstimada();
        if (!llegada.isAfter(salida)) {
            throw new SolicitudInvalidaException("La llegada estimada debe ser posterior a la salida");
        }
        boolean cambiaBus = idBusNuevo != null && !idBusNuevo.equals(v.getIdBus());
        validarRecursos(idBusNuevo, idChoferNuevo, v.getIdSucursalOrigen(), llegada, v.getNumeroPasajeros());

        if (cambiaBus && v.getTipoViaje() == TipoViaje.REGULAR) {
            int asientoMaximo = boletosClient.asientoMaximo(v.getId());
            BusRef bus = busRefRepository.findById(idBusNuevo).orElse(null);
            if (bus != null && bus.getCapacidadPasajeros() < asientoMaximo) {
                throw new ReglaNegocioException("CAPACIDAD_INSUFICIENTE",
                        "El bus tiene capacidad " + bus.getCapacidadPasajeros()
                                + " pero el viaje ya tiene boletos hasta el asiento " + asientoMaximo);
            }
        }
        v.setIdBus(idBusNuevo);
        v.setIdChofer(idChoferNuevo);
        v.setFechaHoraSalida(salida);
        v.setFechaHoraLlegadaEstimada(llegada);
        v = guardarConTraduccionTraslape(v);
        eventos.viajeActualizado(v, busAsignado(v.getIdBus()), choferAsignado(v.getIdChofer()));
        return toResponse(v);
    }

    // ------------------------------------------------------------------
    // Asignar bus y chofer a un alquiler pendiente
    // ------------------------------------------------------------------

    @Transactional
    public Responses.ViajeResponse asignar(UUID id, Requests.AsignacionRequest request,
                                           UsuarioAutenticado usuario) {
        Viaje v = cargar(id);
        autorizarEscritura(v, usuario);
        if (v.getTipoViaje() != TipoViaje.ALQUILER_PRIVADO) {
            throw new ReglaNegocioException("TIPO_VIAJE_INVALIDO",
                    "La asignación de recursos es solo para viajes de alquiler");
        }
        if (v.getEstado() != EstadoViaje.PENDIENTE_ASIGNACION) {
            throw new ReglaNegocioException("ESTADO_INVALIDO",
                    "El viaje no está pendiente de asignación");
        }
        validarRecursos(request.idBus(), request.idChofer(), v.getIdSucursalOrigen(),
                v.getFechaHoraLlegadaEstimada(), v.getNumeroPasajeros());
        v.setIdBus(request.idBus());
        v.setIdChofer(request.idChofer());
        v.setEstado(EstadoViaje.PROGRAMADO);
        v = guardarConTraduccionTraslape(v);
        eventos.viajeActualizado(v, busAsignado(v.getIdBus()), choferAsignado(v.getIdChofer()));
        return toResponse(v);
    }

    // ------------------------------------------------------------------
    // Eliminar
    // ------------------------------------------------------------------

    @Transactional
    public void eliminar(UUID id, UsuarioAutenticado usuario) {
        Viaje v = cargar(id);
        autorizarEscritura(v, usuario);
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

    // ------------------------------------------------------------------
    // Salida (inmutable)
    // ------------------------------------------------------------------

    @Transactional
    public Responses.ViajeResponse registrarSalida(UUID id, Requests.SalidaRequest request,
                                                   UsuarioAutenticado usuario) {
        Viaje v = cargar(id);
        autorizarSalidaLlegada(v, usuario);
        if (v.getEstado() != EstadoViaje.PROGRAMADO) {
            throw new ReglaNegocioException("ESTADO_INVALIDO",
                    "Solo se puede registrar la salida de un viaje PROGRAMADO");
        }
        if (registroSalidaRepository.existsByViajeId(id)) {
            throw new ReglaNegocioException("SALIDA_YA_REGISTRADA", "La salida ya fue registrada");
        }
        RegistroSalida registro = RegistroSalida.builder()
                .viaje(v)
                .idBus(v.getIdBus())
                .idChofer(v.getIdChofer())
                .fechaHoraSalidaReal(request.fechaHoraSalidaReal())
                .kilometrajeInicial(request.kilometrajeInicial())
                .registradoPor(usuario.id())
                .build();
        registroSalidaRepository.save(registro);
        v.setEstado(EstadoViaje.EN_TRANSITO);
        // flush: los eventos deben llevar la versión ya incrementada (los consumidores descartan versiones repetidas)
        v = viajeRepository.saveAndFlush(v);
        eventos.salidaRegistrada(v, request.fechaHoraSalidaReal(), request.kilometrajeInicial(), usuario.id());
        // Catálogo §2.3: el cambio de estado (EN_TRANSITO) también se publica para Boletos, Alquileres y Reportes
        eventos.viajeActualizado(v, busAsignado(v.getIdBus()), choferAsignado(v.getIdChofer()));
        log.info("Salida del viaje {} registrada por {}", id, usuario.id());
        return toResponse(v);
    }

    // ------------------------------------------------------------------
    // Llegada (inmutable; cierra el viaje)
    // ------------------------------------------------------------------

    @Transactional
    public Responses.ViajeResponse registrarLlegada(UUID id, Requests.LlegadaRequest request,
                                                    UsuarioAutenticado usuario) {
        Viaje v = cargar(id);
        autorizarSalidaLlegada(v, usuario);
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
        BigDecimal salarioBase = chofer != null ? chofer.getSalarioBaseViaje() : BigDecimal.ZERO;
        UUID idSucursalBus = bus != null ? bus.getIdSucursal() : v.getIdSucursalOrigen();

        RegistroLlegada llegada = RegistroLlegada.builder()
                .viaje(v)
                .fechaHoraLlegadaReal(request.fechaHoraLlegadaReal())
                .kilometrajeFinal(request.kilometrajeFinal())
                .distanciaRecorridaKm(distancia)
                .gastoCombustibleTotal(request.gastoCombustibleTotal())
                .salarioBaseChofer(salarioBase)
                .registradoPor(usuario.id())
                .build();
        registroLlegadaRepository.save(llegada);
        v.setEstado(EstadoViaje.FINALIZADO);
        // flush: los eventos deben llevar la versión ya incrementada (los consumidores descartan versiones repetidas)
        v = viajeRepository.saveAndFlush(v);
        eventos.llegadaRegistrada(v, idSucursalBus, salarioBase,
                salida.getFechaHoraSalidaReal(), request.fechaHoraLlegadaReal(),
                salida.getKilometrajeInicial(), request.kilometrajeFinal(),
                distancia, request.gastoCombustibleTotal(), usuario.id());
        // Catálogo §2.3: el cambio de estado (FINALIZADO) también se publica
        eventos.viajeActualizado(v, busAsignado(v.getIdBus()), choferAsignado(v.getIdChofer()));
        log.info("Llegada del viaje {} registrada; viaje FINALIZADO", id);
        return toResponse(v);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Viaje cargar(UUID id) {
        return viajeRepository.findConRutaById(id).orElseThrow(() -> new ViajeNoEncontradoException(id));
    }

    private void autorizarLectura(Viaje v, UsuarioAutenticado usuario) {
        if (usuario.esAdminSistema()) {
            return;
        }
        // Plan §3.3: SUC y CAJ ven los viajes de su sucursal (el cajero solo consulta)
        if (usuario.esAdminSucursal() || usuario.rol() == Rol.CAJERO) {
            if (!usuario.perteneceASucursal(v.getIdSucursalOrigen())) {
                throw new OperacionNoAutorizadaException("El viaje no pertenece a su sucursal");
            }
            return;
        }
        // CHOFER: solo los suyos
        if (v.getIdChofer() == null || !esSuChofer(v.getIdChofer(), usuario)) {
            throw new OperacionNoAutorizadaException("Solo puede ver sus propios viajes");
        }
    }

    private void autorizarEscritura(Viaje v, UsuarioAutenticado usuario) {
        if (!usuario.esAdminSucursal() || !usuario.perteneceASucursal(v.getIdSucursalOrigen())) {
            throw new OperacionNoAutorizadaException("Solo el administrador de la sucursal del viaje puede modificarlo");
        }
    }

    private void autorizarSalidaLlegada(Viaje v, UsuarioAutenticado usuario) {
        if (usuario.esAdminSucursal()) {
            if (!usuario.perteneceASucursal(v.getIdSucursalOrigen())) {
                throw new OperacionNoAutorizadaException("El viaje no pertenece a su sucursal");
            }
            return;
        }
        if (v.getIdChofer() == null || !esSuChofer(v.getIdChofer(), usuario)) {
            throw new OperacionNoAutorizadaException("Solo puede registrar salida o llegada de sus propios viajes");
        }
    }

    private boolean puedeVer(Viaje v, UsuarioAutenticado usuario) {
        if (usuario.esAdminSistema()) {
            return true;
        }
        if (usuario.esAdminSucursal() || usuario.rol() == Rol.CAJERO) {
            return usuario.perteneceASucursal(v.getIdSucursalOrigen());
        }
        return v.getIdChofer() != null && esSuChofer(v.getIdChofer(), usuario);
    }

    private boolean esSuChofer(UUID idChofer, UsuarioAutenticado usuario) {
        return choferRefRepository.findById(idChofer)
                .map(c -> usuario.id().equals(c.getIdUsuario()))
                .orElse(false);
    }

    private void validarRecursos(UUID idBus, UUID idChofer, UUID idSucursalViaje,
                                 OffsetDateTime llegadaEstimada, Short numeroPasajeros) {
        if (idBus != null) {
            BusRef bus = busRefRepository.findById(idBus)
                    .orElseThrow(() -> new ReglaNegocioException("BUS_NO_ENCONTRADO",
                            "El bus " + idBus + " no existe"));
            if (!bus.getActivo()) {
                throw new ReglaNegocioException("BUS_INACTIVO", "El bus asignado está inactivo");
            }
            if (!bus.getIdSucursal().equals(idSucursalViaje)) {
                throw new ReglaNegocioException("BUS_OTRA_SUCURSAL", "El bus no pertenece a la sucursal del viaje");
            }
            if (bus.getEstadoOperativo() == EstadoOperativo.EN_TALLER
                    || bus.getEstadoOperativo() == EstadoOperativo.FUERA_DE_SERVICIO) {
                throw new ReglaNegocioException("BUS_NO_DISPONIBLE", "El bus está en taller o fuera de servicio");
            }
            if (numeroPasajeros != null && bus.getCapacidadPasajeros() < numeroPasajeros) {
                throw new ReglaNegocioException("CAPACIDAD_INSUFICIENTE",
                        "El bus (" + bus.getCapacidadPasajeros() + " asientos) no alcanza para "
                                + numeroPasajeros + " pasajeros");
            }
        }
        if (idChofer != null) {
            ChoferRef chofer = choferRefRepository.findById(idChofer)
                    .orElseThrow(() -> new ReglaNegocioException("CHOFER_NO_ENCONTRADO",
                            "El chofer " + idChofer + " no existe"));
            if (!chofer.getActivo()) {
                throw new ReglaNegocioException("CHOFER_INACTIVO", "El chofer asignado está inactivo");
            }
            if (!chofer.getIdSucursal().equals(idSucursalViaje)) {
                throw new ReglaNegocioException("CHOFER_OTRA_SUCURSAL",
                        "El chofer no pertenece a la sucursal del viaje");
            }
            if (chofer.getFechaVencimientoLicencia() != null && llegadaEstimada != null
                    && chofer.getFechaVencimientoLicencia().isBefore(llegadaEstimada.toLocalDate())) {
                throw new ReglaNegocioException("LICENCIA_VENCIDA",
                        "La licencia del chofer vence antes de finalizar el viaje");
            }
        }
    }

    /**
     * Guarda con flush inmediato por dos motivos: los eventos se registran después con la versión ya incrementada
     * (sin flush saldrían con la anterior y Boletos, Alquileres y Reportes los descartarían) y el EXCLUDE de
     * traslape salta aquí, donde se traduce a CONFLICTO_HORARIO, y no al confirmar la transacción.
     */
    private Viaje guardarConTraduccionTraslape(Viaje v) {
        try {
            return viajeRepository.saveAndFlush(v);
        } catch (org.springframework.dao.DataAccessException e) {
            if (esTraslape(e)) {
                throw new ReglaNegocioException("CONFLICTO_HORARIO",
                        "El bus o el chofer ya tienen un viaje que se traslapa");
            }
            throw e;
        }
    }

    private boolean esTraslape(Throwable e) {
        Throwable c = e;
        while (c != null) {
            if (c instanceof java.sql.SQLException sql && "23P01".equals(sql.getSQLState())) {
                return true;
            }
            c = c.getCause();
        }
        return false;
    }

    private BusAsignado busAsignado(UUID idBus) {
        if (idBus == null) {
            return null;
        }
        return busRefRepository.findById(idBus)
                .map(b -> new BusAsignado(b.getId(), b.getPlaca(), b.getCapacidadPasajeros()))
                .orElse(null);
    }

    private ChoferAsignado choferAsignado(UUID idChofer) {
        if (idChofer == null) {
            return null;
        }
        return choferRefRepository.findById(idChofer)
                .map(c -> new ChoferAsignado(c.getId(), c.getNombreCompleto()))
                .orElse(null);
    }

    private String sucursalNombre(UUID idSucursal) {
        return sucursalRefRepository.findById(idSucursal)
                .map(SucursalRef::getNombre)
                .orElse("Origen");
    }

    private Responses.ViajeResponse toResponse(Viaje v) {
        Viaje conRuta = viajeRepository.findConRutaById(v.getId()).orElse(v);
        Responses.BusResumen bus = busAsignado(conRuta.getIdBus()) == null ? null
                : busRefRepository.findById(conRuta.getIdBus())
                        .map(b -> new Responses.BusResumen(b.getId(), b.getPlaca(),
                                (int) b.getCapacidadPasajeros(), b.getEstadoOperativo(), b.getActivo()))
                        .orElse(null);
        Responses.ChoferResumen chofer = choferAsignado(conRuta.getIdChofer()) == null ? null
                : choferRefRepository.findById(conRuta.getIdChofer())
                        .map(c -> new Responses.ChoferResumen(c.getId(), c.getNombreCompleto(),
                                c.getFechaVencimientoLicencia() == null ? null
                                        : c.getFechaVencimientoLicencia().atStartOfDay().atOffset(java.time.ZoneOffset.UTC),
                                c.getSalarioBaseViaje(), c.getActivo()))
                        .orElse(null);
        Responses.RegistroSalidaResponse regSalida = registroSalidaRepository.findByViajeId(conRuta.getId())
                .map(s -> new Responses.RegistroSalidaResponse(s.getId(), s.getFechaHoraSalidaReal(),
                        s.getKilometrajeInicial(), s.getRegistradoPor()))
                .orElse(null);
        Responses.RegistroLlegadaResponse regLlegada = registroLlegadaRepository.findByViajeId(conRuta.getId())
                .map(l -> new Responses.RegistroLlegadaResponse(l.getId(), l.getFechaHoraLlegadaReal(),
                        l.getKilometrajeFinal(), l.getDistanciaRecorridaKm(), l.getGastoCombustibleTotal(),
                        l.getSalarioBaseChofer(), l.getRegistradoPor()))
                .orElse(null);
        return new Responses.ViajeResponse(
                conRuta.getId(), conRuta.getTipoViaje(), conRuta.getEstado(),
                conRuta.getRuta() == null ? null : conRuta.getRuta().getId(),
                conRuta.getIdSucursalOrigen(), conRuta.getNombreOrigen(), conRuta.getNombreDestino(),
                conRuta.getIdAlquiler(), conRuta.getFechaHoraSalida(), conRuta.getFechaHoraLlegadaEstimada(),
                conRuta.getPrecioBoleto(), conRuta.getNumeroPasajeros(),
                bus, chofer, regSalida, regLlegada, conRuta.getVersion());
    }
}
