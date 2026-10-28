package org.transportsgt.tripservice.services.viaje.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.payload.viajes.EstadoViaje;
import org.transportsgt.eventos.payload.viajes.TipoViaje;
import org.transportsgt.tripservice.clients.BoletosClient;
import org.transportsgt.tripservice.dto.request.Requests;
import org.transportsgt.tripservice.eventos.EventosViaje;
import org.transportsgt.tripservice.eventos.RecursosEvento;
import org.transportsgt.tripservice.exception.OperacionNoAutorizadaException;
import org.transportsgt.tripservice.exception.ReglaNegocioException;
import org.transportsgt.tripservice.exception.ServicioNoDisponibleException;
import org.transportsgt.tripservice.exception.SolicitudInvalidaException;
import org.transportsgt.tripservice.mappers.EnsambladorViajes;
import org.transportsgt.tripservice.mappers.ViajeMapperImpl;
import org.transportsgt.tripservice.models.Viaje;
import org.transportsgt.tripservice.repositories.BusRefRepository;
import org.transportsgt.tripservice.repositories.ChoferRefRepository;
import org.transportsgt.tripservice.repositories.RegistroLlegadaRepository;
import org.transportsgt.tripservice.repositories.RegistroSalidaRepository;
import org.transportsgt.tripservice.repositories.RutaRepository;
import org.transportsgt.tripservice.repositories.SucursalRefRepository;
import org.transportsgt.tripservice.repositories.ViajeRepository;
import org.transportsgt.tripservice.services.autorizacion.AutorizacionViaje;
import org.transportsgt.tripservice.services.comun.BuscadorViajes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgramacionViajesServiceImplTest {

    @Mock
    private ViajeRepository viajeRepository;
    @Mock
    private RutaRepository rutaRepository;
    @Mock
    private BusRefRepository busRefRepository;
    @Mock
    private ChoferRefRepository choferRefRepository;
    @Mock
    private SucursalRefRepository sucursalRefRepository;
    @Mock
    private RegistroSalidaRepository registroSalidaRepository;
    @Mock
    private RegistroLlegadaRepository registroLlegadaRepository;
    @Mock
    private BoletosClient boletosClient;
    @Mock
    private EventosViaje eventos;

    private final FixturesViaje f = new FixturesViaje();
    private ProgramacionViajesServiceImpl servicio;

    @BeforeEach
    void setUp() {
        servicio = new ProgramacionViajesServiceImpl(viajeRepository, rutaRepository, busRefRepository,
                sucursalRefRepository, new BuscadorViajes(viajeRepository), new AutorizacionViaje(choferRefRepository),
                new ValidadorRecursos(busRefRepository, choferRefRepository), new GuardadoViaje(viajeRepository),
                boletosClient, new RecursosEvento(busRefRepository, choferRefRepository), eventos,
                new EnsambladorViajes(busRefRepository, choferRefRepository, registroSalidaRepository,
                        registroLlegadaRepository, new ViajeMapperImpl()));
    }

    // ------------------------------------------------------------------
    // Crear regular
    // ------------------------------------------------------------------

    @Test
    void crearRegularValidaRecursosYPublicaViajeProgramado() {
        Viaje guardado = f.viajeProgramado();
        Requests.ViajeRegularRequest req = new Requests.ViajeRegularRequest(
                f.rutaId, f.bus1, f.chofer1, guardado.getFechaHoraSalida(), guardado.getFechaHoraLlegadaEstimada());
        when(rutaRepository.findById(f.rutaId)).thenReturn(Optional.of(f.ruta()));
        when(busRefRepository.findById(f.bus1)).thenReturn(Optional.of(f.busActivo()));
        when(choferRefRepository.findById(f.chofer1)).thenReturn(Optional.of(f.choferRef()));
        when(sucursalRefRepository.findById(f.xela)).thenReturn(Optional.empty());
        when(viajeRepository.saveAndFlush(any(Viaje.class))).thenReturn(guardado);

        var resp = servicio.crearRegular(req, f.adminSucursal);

        assertEquals(guardado.getId(), resp.id());
        assertEquals(EstadoViaje.PROGRAMADO, resp.estado());
        // Catálogo §2.3: un viaje nuevo se publica como ViajeProgramado, no como ViajeActualizado
        verify(eventos).viajeProgramado(any(), any(), any());
        verify(eventos, never()).viajeActualizado(any(), any(), any());
    }

    @Test
    void crearRegularRechazaOtraSucursal() {
        Requests.ViajeRegularRequest req = new Requests.ViajeRegularRequest(
                f.rutaId, f.bus1, f.chofer1, OffsetDateTime.now().plusDays(7),
                OffsetDateTime.now().plusDays(7).plusHours(4));
        when(rutaRepository.findById(f.rutaId)).thenReturn(Optional.of(f.ruta()));

        assertThrows(OperacionNoAutorizadaException.class,
                () -> servicio.crearRegular(req, f.adminDe(f.capital)));
    }

    @Test
    void crearRegularRechazaLlegadaAntesDeSalida() {
        Requests.ViajeRegularRequest req = new Requests.ViajeRegularRequest(
                f.rutaId, f.bus1, f.chofer1, OffsetDateTime.now().plusDays(7),
                OffsetDateTime.now().plusDays(6));
        when(rutaRepository.findById(f.rutaId)).thenReturn(Optional.of(f.ruta()));

        assertThrows(SolicitudInvalidaException.class, () -> servicio.crearRegular(req, f.adminSucursal));
    }

    @Test
    void crearRegularRechazaBusDeOtraSucursal() {
        Requests.ViajeRegularRequest req = new Requests.ViajeRegularRequest(
                f.rutaId, f.bus1, f.chofer1, OffsetDateTime.now().plusDays(7),
                OffsetDateTime.now().plusDays(7).plusHours(4));
        when(rutaRepository.findById(f.rutaId)).thenReturn(Optional.of(f.ruta()));
        when(busRefRepository.findById(f.bus1)).thenReturn(Optional.of(f.bus(f.bus1, f.capital, true)));

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.crearRegular(req, f.adminSucursal));
        assertEquals("BUS_OTRA_SUCURSAL", ex.getCodigo());
    }

    /** La licencia vale hasta su día de vencimiento inclusive, contado en hora de Guatemala. */
    @Test
    void crearRegularAceptaLicenciaQueVenceElDiaDeLlegadaEnGuatemala() {
        // 23:30 de Guatemala del 10 de diciembre llega expresada en UTC (ya es 11 en UTC)
        OffsetDateTime salida = OffsetDateTime.parse("2030-12-10T18:00:00-06:00");
        OffsetDateTime llegada = OffsetDateTime.parse("2030-12-11T05:30:00Z");
        var chofer = f.choferRef();
        chofer.setFechaVencimientoLicencia(LocalDate.of(2030, 12, 10));
        Viaje guardado = f.viajeProgramado();
        when(rutaRepository.findById(f.rutaId)).thenReturn(Optional.of(f.ruta()));
        when(busRefRepository.findById(f.bus1)).thenReturn(Optional.of(f.busActivo()));
        when(choferRefRepository.findById(f.chofer1)).thenReturn(Optional.of(chofer));
        when(sucursalRefRepository.findById(f.xela)).thenReturn(Optional.empty());
        when(viajeRepository.saveAndFlush(any(Viaje.class))).thenReturn(guardado);

        servicio.crearRegular(new Requests.ViajeRegularRequest(f.rutaId, f.bus1, f.chofer1, salida, llegada),
                f.adminSucursal);

        verify(eventos).viajeProgramado(any(), any(), any());
    }

    @Test
    void crearRegularRechazaLicenciaQueVenceAntesDeLaLlegada() {
        // 01:00 de Guatemala del 11 de diciembre (con otra zona, la fecha local sería todavía el 10)
        OffsetDateTime salida = OffsetDateTime.parse("2030-12-10T18:00:00-06:00");
        OffsetDateTime llegada = OffsetDateTime.parse("2030-12-10T19:00:00-12:00");
        var chofer = f.choferRef();
        chofer.setFechaVencimientoLicencia(LocalDate.of(2030, 12, 10));
        when(rutaRepository.findById(f.rutaId)).thenReturn(Optional.of(f.ruta()));
        when(busRefRepository.findById(f.bus1)).thenReturn(Optional.of(f.busActivo()));
        when(choferRefRepository.findById(f.chofer1)).thenReturn(Optional.of(chofer));

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class, () -> servicio.crearRegular(
                new Requests.ViajeRegularRequest(f.rutaId, f.bus1, f.chofer1, salida, llegada), f.adminSucursal));
        assertEquals("LICENCIA_VENCIDA", ex.getCodigo());
    }

    // ------------------------------------------------------------------
    // Actualizar
    // ------------------------------------------------------------------

    @Test
    void actualizarCambiaBusYConsultaAsientoMaximo() {
        Viaje v = f.viajeProgramado();
        Viaje otro = f.viajeProgramado();
        otro.setIdBus(UUID.fromString("20000000-0000-4000-8000-000000000002"));
        Requests.ViajeUpdateRequest req = new Requests.ViajeUpdateRequest(otro.getIdBus(), null, null, null);
        when(viajeRepository.findConRutaById(v.getId())).thenReturn(Optional.of(v));
        when(busRefRepository.findById(otro.getIdBus())).thenReturn(Optional.of(f.bus(otro.getIdBus(), f.xela, true)));
        when(choferRefRepository.findById(f.chofer1)).thenReturn(Optional.of(f.choferRef()));
        when(boletosClient.asientoMaximo(v.getId())).thenReturn(40);
        when(viajeRepository.saveAndFlush(any(Viaje.class))).thenReturn(otro);

        var resp = servicio.actualizar(v.getId(), req, f.adminSucursal);

        assertNotNull(resp);
        verify(boletosClient).asientoMaximo(v.getId());
    }

    @Test
    void actualizarFallaCerradoCuandoBoletosNoResponde() {
        Viaje v = f.viajeProgramado();
        UUID busNuevo = UUID.fromString("20000000-0000-4000-8000-000000000002");
        Requests.ViajeUpdateRequest req = new Requests.ViajeUpdateRequest(busNuevo, null, null, null);
        when(viajeRepository.findConRutaById(v.getId())).thenReturn(Optional.of(v));
        when(busRefRepository.findById(any())).thenReturn(Optional.of(f.bus(busNuevo, f.xela, true)));
        when(choferRefRepository.findById(f.chofer1)).thenReturn(Optional.of(f.choferRef()));
        when(boletosClient.asientoMaximo(v.getId()))
                .thenThrow(new ServicioNoDisponibleException("Boletos no responde"));

        assertThrows(ServicioNoDisponibleException.class,
                () -> servicio.actualizar(v.getId(), req, f.adminSucursal));
    }

    @Test
    void actualizarRechazaViajeEnTransito() {
        Viaje v = f.viajeProgramado();
        v.setEstado(EstadoViaje.EN_TRANSITO);
        when(viajeRepository.findConRutaById(v.getId())).thenReturn(Optional.of(v));

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.actualizar(v.getId(), new Requests.ViajeUpdateRequest(null, null, null, null),
                        f.adminSucursal));
        assertEquals("VIAJE_NO_MODIFICABLE", ex.getCodigo());
    }

    @Test
    void actualizarConElMismoBusYNuevosHorariosNoConsultaBoletos() {
        Viaje v = f.viajeProgramado();
        OffsetDateTime salida = v.getFechaHoraSalida().plusHours(1);
        Requests.ViajeUpdateRequest req = new Requests.ViajeUpdateRequest(v.getIdBus(), f.chofer1, salida,
                salida.plusHours(4));
        when(viajeRepository.findConRutaById(v.getId())).thenReturn(Optional.of(v));
        when(busRefRepository.findById(v.getIdBus())).thenReturn(Optional.of(f.busActivo()));
        when(choferRefRepository.findById(f.chofer1)).thenReturn(Optional.of(f.choferRef()));
        when(viajeRepository.saveAndFlush(any(Viaje.class))).thenAnswer(inv -> inv.getArgument(0));

        servicio.actualizar(v.getId(), req, f.adminSucursal);

        assertEquals(salida, v.getFechaHoraSalida());
        verify(boletosClient, never()).asientoMaximo(any());
    }

    @Test
    void actualizarRechazaViajeFinalizado() {
        Viaje v = f.viajeProgramado();
        v.setEstado(EstadoViaje.FINALIZADO);
        when(viajeRepository.findConRutaById(v.getId())).thenReturn(Optional.of(v));

        assertThrows(ReglaNegocioException.class,
                () -> servicio.actualizar(v.getId(), new Requests.ViajeUpdateRequest(null, null, null, null),
                        f.adminSucursal));
    }

    // ------------------------------------------------------------------
    // Asignar alquiler
    // ------------------------------------------------------------------

    @Test
    void asignarCambiaPendienteAProgramado() {
        Viaje v = f.viajeProgramado();
        v.setTipoViaje(TipoViaje.ALQUILER_PRIVADO);
        v.setEstado(EstadoViaje.PENDIENTE_ASIGNACION);
        v.setIdBus(null);
        v.setIdChofer(null);
        v.setRuta(null);
        v.setNumeroPasajeros((short) 30);
        Requests.AsignacionRequest req = new Requests.AsignacionRequest(f.bus1, f.chofer1);
        Viaje asignado = f.viajeProgramado();
        asignado.setTipoViaje(TipoViaje.ALQUILER_PRIVADO);
        when(viajeRepository.findConRutaById(v.getId())).thenReturn(Optional.of(v));
        when(busRefRepository.findById(f.bus1)).thenReturn(Optional.of(f.busActivo()));
        when(choferRefRepository.findById(f.chofer1)).thenReturn(Optional.of(f.choferRef()));
        when(viajeRepository.saveAndFlush(any(Viaje.class))).thenReturn(asignado);

        var resp = servicio.asignar(v.getId(), req, f.adminSucursal);

        assertEquals(EstadoViaje.PROGRAMADO, resp.estado());
        verify(eventos).viajeActualizado(any(), any(), any());
    }

    @Test
    void asignarRechazaViajeRegular() {
        Viaje v = f.viajeProgramado();
        when(viajeRepository.findConRutaById(v.getId())).thenReturn(Optional.of(v));

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.asignar(v.getId(), new Requests.AsignacionRequest(f.bus1, f.chofer1),
                        f.adminSucursal));
        assertEquals("TIPO_VIAJE_INVALIDO", ex.getCodigo());
    }

    @Test
    void asignarRechazaBusInactivo() {
        Viaje v = f.viajeProgramado();
        v.setTipoViaje(TipoViaje.ALQUILER_PRIVADO);
        v.setEstado(EstadoViaje.PENDIENTE_ASIGNACION);
        when(viajeRepository.findConRutaById(v.getId())).thenReturn(Optional.of(v));
        when(busRefRepository.findById(f.bus1)).thenReturn(Optional.of(f.bus(f.bus1, f.xela, false)));

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.asignar(v.getId(), new Requests.AsignacionRequest(f.bus1, f.chofer1),
                        f.adminSucursal));
        assertEquals("BUS_INACTIVO", ex.getCodigo());
    }

    // ------------------------------------------------------------------
    // Eliminar
    // ------------------------------------------------------------------

    @Test
    void eliminarPublicaViajeEliminado() {
        Viaje v = f.viajeProgramado();
        when(viajeRepository.findConRutaById(v.getId())).thenReturn(Optional.of(v));

        servicio.eliminar(v.getId(), f.adminSucursal);

        verify(viajeRepository).delete(eq(v));
        verify(eventos).viajeEliminado(any(), eq(f.adminSucursal.id()));
    }

    @Test
    void eliminarRechazaViajeEnTransito() {
        Viaje v = f.viajeProgramado();
        v.setEstado(EstadoViaje.EN_TRANSITO);
        when(viajeRepository.findConRutaById(v.getId())).thenReturn(Optional.of(v));

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.eliminar(v.getId(), f.adminSucursal));
        assertEquals("VIAJE_NO_ELIMINABLE", ex.getCodigo());
        verify(viajeRepository, never()).delete(eq(v));
    }

    @Test
    void eliminarRechazaOtraSucursal() {
        Viaje v = f.viajeProgramado();
        when(viajeRepository.findConRutaById(v.getId())).thenReturn(Optional.of(v));

        assertThrows(OperacionNoAutorizadaException.class,
                () -> servicio.eliminar(v.getId(), f.adminDe(f.capital)));
    }
}
