package org.transportsgt.tripservice.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.payload.EstadoOperativo;
import org.transportsgt.eventos.payload.EstadoViaje;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.eventos.payload.TipoViaje;
import org.transportsgt.tripservice.clients.BoletosClient;
import org.transportsgt.tripservice.config.security.UsuarioAutenticado;
import org.transportsgt.tripservice.dto.request.Requests;
import org.transportsgt.tripservice.eventos.EventosViaje;
import org.transportsgt.tripservice.exception.OperacionNoAutorizadaException;
import org.transportsgt.tripservice.exception.ReglaNegocioException;
import org.transportsgt.tripservice.exception.ServicioNoDisponibleException;
import org.transportsgt.tripservice.exception.SolicitudInvalidaException;
import org.transportsgt.tripservice.exception.ViajeNoEncontradoException;
import org.transportsgt.tripservice.models.BusRef;
import org.transportsgt.tripservice.models.ChoferRef;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViajeServiceTest {

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

    private ViajeService servicio;

    private final UUID XELA = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private final UUID CAPITAL = UUID.fromString("10000000-0000-4000-8000-000000000002");
    private final UUID BUS_1 = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private final UUID CHOFER_1 = UUID.fromString("30000000-0000-4000-8000-000000000001");
    private final UUID RUTA_ID = UUID.fromString("40000000-0000-4000-8000-000000000001");
    private final UUID USUARIO_CHOFER = UUID.fromString("e0000000-0000-4000-8000-000000000001");

    private final UsuarioAutenticado adminSucursal = new UsuarioAutenticado(
            UUID.randomUUID(), "admin@transportes.gt", Rol.ADMIN_SUCURSAL, XELA, "Admin Xela");
    private final UsuarioAutenticado adminSistema = new UsuarioAutenticado(
            UUID.randomUUID(), "sis@transportes.gt", Rol.ADMIN_SISTEMA, null, "SIS");
    private final UsuarioAutenticado chofer = new UsuarioAutenticado(
            USUARIO_CHOFER, "chofer@transportes.gt", Rol.CHOFER, XELA, "Juan Pérez");

    @BeforeEach
    void setUp() {
        servicio = new ViajeService(viajeRepository, rutaRepository, busRefRepository,
                choferRefRepository, sucursalRefRepository, registroSalidaRepository,
                registroLlegadaRepository, boletosClient, eventos);
    }

    private Ruta ruta() {
        return Ruta.builder().id(RUTA_ID).idSucursalOrigen(XELA).idSucursalDestino(CAPITAL)
                .distanciaKm(new BigDecimal("205.50")).precioBoleto(new BigDecimal("85.00"))
                .creadoPor(adminSucursal.id()).version(1L).build();
    }

    private BusRef busActivo() {
        return BusRef.builder().id(BUS_1).idSucursal(XELA).placa("P123ABC")
                .capacidadPasajeros((short) 45).estadoOperativo(EstadoOperativo.DISPONIBLE)
                .activo(true).version(1L).build();
    }

    private ChoferRef choferRef() {
        return ChoferRef.builder().id(CHOFER_1).idUsuario(USUARIO_CHOFER).idSucursal(XELA)
                .nombreCompleto("Juan Pérez").salarioBaseViaje(new BigDecimal("150.00"))
                .fechaVencimientoLicencia(LocalDate.now().plusYears(2)).activo(true).version(1L).build();
    }

    private Viaje viajeProgramado() {
        Viaje v = Viaje.builder()
                .id(UUID.randomUUID())
                .tipoViaje(TipoViaje.REGULAR)
                .estado(EstadoViaje.PROGRAMADO)
                .ruta(ruta())
                .idSucursalOrigen(XELA)
                .nombreOrigen("Quetzaltenango")
                .nombreDestino("Ciudad de Guatemala")
                .idBus(BUS_1)
                .idChofer(CHOFER_1)
                .fechaHoraSalida(OffsetDateTime.now().plusDays(7))
                .fechaHoraLlegadaEstimada(OffsetDateTime.now().plusDays(7).plusHours(4))
                .precioBoleto(new BigDecimal("85.00"))
                .creadoPor(adminSucursal.id())
                .version(0L)
                .build();
        v.setVersion(1L);
        return v;
    }

    // ------------------------------------------------------------------
    // Crear regular
    // ------------------------------------------------------------------

    @Test
    void crearRegularValidaRecursosYPublicaViajeActualizado() {
        Viaje guardado = viajeProgramado();
        Requests.ViajeRegularRequest req = new Requests.ViajeRegularRequest(
                RUTA_ID, BUS_1, CHOFER_1, guardado.getFechaHoraSalida(), guardado.getFechaHoraLlegadaEstimada());
        when(rutaRepository.findById(RUTA_ID)).thenReturn(Optional.of(ruta()));
        when(busRefRepository.findById(BUS_1)).thenReturn(Optional.of(busActivo()));
        when(choferRefRepository.findById(CHOFER_1)).thenReturn(Optional.of(choferRef()));
        when(sucursalRefRepository.findById(XELA)).thenReturn(Optional.empty());
        when(viajeRepository.save(any(Viaje.class))).thenReturn(guardado);
        when(viajeRepository.findConRutaById(guardado.getId())).thenReturn(Optional.of(guardado));

        var resp = servicio.crearRegular(req, adminSucursal);

        assertEquals(guardado.getId(), resp.id());
        assertEquals(EstadoViaje.PROGRAMADO, resp.estado());
        verify(eventos).viajeActualizado(any(), any(), any());
    }

    @Test
    void crearRegularRechazaOtraSucursal() {
        UsuarioAutenticado adminCapital = new UsuarioAutenticado(
                UUID.randomUUID(), "cap@transportes.gt", Rol.ADMIN_SUCURSAL, CAPITAL, "Admin Cap");
        Requests.ViajeRegularRequest req = new Requests.ViajeRegularRequest(
                RUTA_ID, BUS_1, CHOFER_1, OffsetDateTime.now().plusDays(7),
                OffsetDateTime.now().plusDays(7).plusHours(4));
        when(rutaRepository.findById(RUTA_ID)).thenReturn(Optional.of(ruta()));

        assertThrows(OperacionNoAutorizadaException.class,
                () -> servicio.crearRegular(req, adminCapital));
    }

    @Test
    void crearRegularRechazaLlegadaAntesDeSalida() {
        Requests.ViajeRegularRequest req = new Requests.ViajeRegularRequest(
                RUTA_ID, BUS_1, CHOFER_1, OffsetDateTime.now().plusDays(7),
                OffsetDateTime.now().plusDays(6));
        when(rutaRepository.findById(RUTA_ID)).thenReturn(Optional.of(ruta()));

        assertThrows(SolicitudInvalidaException.class, () -> servicio.crearRegular(req, adminSucursal));
    }

    @Test
    void crearRegularRechazaBusDeOtraSucursal() {
        Requests.ViajeRegularRequest req = new Requests.ViajeRegularRequest(
                RUTA_ID, BUS_1, CHOFER_1, OffsetDateTime.now().plusDays(7),
                OffsetDateTime.now().plusDays(7).plusHours(4));
        BusRef busCapital = BusRef.builder().id(BUS_1).idSucursal(CAPITAL).placa("P999ZZZ")
                .capacidadPasajeros((short) 45).estadoOperativo(EstadoOperativo.DISPONIBLE)
                .activo(true).version(1L).build();
        when(rutaRepository.findById(RUTA_ID)).thenReturn(Optional.of(ruta()));
        when(busRefRepository.findById(BUS_1)).thenReturn(Optional.of(busCapital));

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.crearRegular(req, adminSucursal));
        assertEquals("BUS_OTRA_SUCURSAL", ex.getCodigo());
    }

    // ------------------------------------------------------------------
    // Actualizar
    // ------------------------------------------------------------------

    @Test
    void actualizarCambiaBusYConsultaAsientoMaximo() {
        Viaje v = viajeProgramado();
        Viaje otro = viajeProgramado();
        otro.setIdBus(UUID.fromString("20000000-0000-4000-8000-000000000002"));
        Requests.ViajeUpdateRequest req = new Requests.ViajeUpdateRequest(otro.getIdBus(), null, null, null);
        when(viajeRepository.findConRutaById(v.getId())).thenReturn(Optional.of(v));
        when(busRefRepository.findById(otro.getIdBus())).thenReturn(Optional.of(BusRef.builder()
                .id(otro.getIdBus()).idSucursal(XELA).placa("P456DEF").capacidadPasajeros((short) 45)
                .estadoOperativo(EstadoOperativo.DISPONIBLE).activo(true).version(1L).build()));
        when(choferRefRepository.findById(CHOFER_1)).thenReturn(Optional.of(choferRef()));
        when(boletosClient.asientoMaximo(v.getId())).thenReturn(40);
        when(viajeRepository.save(any(Viaje.class))).thenReturn(otro);
        when(viajeRepository.findConRutaById(otro.getId())).thenReturn(Optional.of(otro));

        var resp = servicio.actualizar(v.getId(), req, adminSucursal);

        assertNotNull(resp);
        verify(boletosClient).asientoMaximo(v.getId());
    }

    @Test
    void actualizarFallaCerradoCuandoBoletosNoResponde() {
        Viaje v = viajeProgramado();
        Requests.ViajeUpdateRequest req = new Requests.ViajeUpdateRequest(
                UUID.fromString("20000000-0000-4000-8000-000000000002"), null, null, null);
        when(viajeRepository.findConRutaById(v.getId())).thenReturn(Optional.of(v));
        when(busRefRepository.findById(any())).thenReturn(Optional.of(BusRef.builder()
                .id(UUID.fromString("20000000-0000-4000-8000-000000000002")).idSucursal(XELA)
                .placa("P456DEF").capacidadPasajeros((short) 45)
                .estadoOperativo(EstadoOperativo.DISPONIBLE).activo(true).version(1L).build()));
        when(choferRefRepository.findById(CHOFER_1)).thenReturn(Optional.of(choferRef()));
        when(boletosClient.asientoMaximo(v.getId()))
                .thenThrow(new ServicioNoDisponibleException("Boletos no responde"));

        assertThrows(ServicioNoDisponibleException.class,
                () -> servicio.actualizar(v.getId(), req, adminSucursal));
    }

    @Test
    void actualizarRechazaViajeEnTransito() {
        Viaje v = viajeProgramado();
        v.setEstado(EstadoViaje.EN_TRANSITO);
        when(viajeRepository.findConRutaById(v.getId())).thenReturn(Optional.of(v));

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.actualizar(v.getId(), new Requests.ViajeUpdateRequest(null, null, null, null),
                        adminSucursal));
        assertEquals("VIAJE_NO_MODIFICABLE", ex.getCodigo());
    }

    // ------------------------------------------------------------------
    // Asignar alquiler
    // ------------------------------------------------------------------

    @Test
    void asignarCambiaPendienteAProgramado() {
        Viaje v = viajeProgramado();
        v.setTipoViaje(TipoViaje.ALQUILER_PRIVADO);
        v.setEstado(EstadoViaje.PENDIENTE_ASIGNACION);
        v.setIdBus(null);
        v.setIdChofer(null);
        v.setRuta(null);
        v.setNumeroPasajeros((short) 30);
        Requests.AsignacionRequest req = new Requests.AsignacionRequest(BUS_1, CHOFER_1);
        Viaje asignado = viajeProgramado();
        asignado.setTipoViaje(TipoViaje.ALQUILER_PRIVADO);
        when(viajeRepository.findConRutaById(v.getId())).thenReturn(Optional.of(v));
        when(busRefRepository.findById(BUS_1)).thenReturn(Optional.of(busActivo()));
        when(choferRefRepository.findById(CHOFER_1)).thenReturn(Optional.of(choferRef()));
        when(viajeRepository.save(any(Viaje.class))).thenReturn(asignado);
        when(viajeRepository.findConRutaById(asignado.getId())).thenReturn(Optional.of(asignado));

        var resp = servicio.asignar(v.getId(), req, adminSucursal);

        assertEquals(EstadoViaje.PROGRAMADO, resp.estado());
        verify(eventos).viajeActualizado(any(), any(), any());
    }

    @Test
    void asignarRechazaViajeRegular() {
        Viaje v = viajeProgramado();
        when(viajeRepository.findConRutaById(v.getId())).thenReturn(Optional.of(v));

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.asignar(v.getId(), new Requests.AsignacionRequest(BUS_1, CHOFER_1),
                        adminSucursal));
        assertEquals("TIPO_VIAJE_INVALIDO", ex.getCodigo());
    }

    @Test
    void asignarRechazaBusInactivo() {
        Viaje v = viajeProgramado();
        v.setTipoViaje(TipoViaje.ALQUILER_PRIVADO);
        v.setEstado(EstadoViaje.PENDIENTE_ASIGNACION);
        BusRef inactivo = BusRef.builder().id(BUS_1).idSucursal(XELA).placa("P123ABC")
                .capacidadPasajeros((short) 45).estadoOperativo(EstadoOperativo.DISPONIBLE)
                .activo(false).version(1L).build();
        when(viajeRepository.findConRutaById(v.getId())).thenReturn(Optional.of(v));
        when(busRefRepository.findById(BUS_1)).thenReturn(Optional.of(inactivo));

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.asignar(v.getId(), new Requests.AsignacionRequest(BUS_1, CHOFER_1),
                        adminSucursal));
        assertEquals("BUS_INACTIVO", ex.getCodigo());
    }

    // ------------------------------------------------------------------
    // Eliminar
    // ------------------------------------------------------------------

    @Test
    void eliminarPublicaViajeEliminado() {
        Viaje v = viajeProgramado();
        when(viajeRepository.findConRutaById(v.getId())).thenReturn(Optional.of(v));

        servicio.eliminar(v.getId(), adminSucursal);

        verify(viajeRepository).delete(eq(v));
        verify(eventos).viajeEliminado(any(), org.mockito.ArgumentMatchers.eq(adminSucursal.id()));
    }

    @Test
    void eliminarRechazaViajeEnTransito() {
        Viaje v = viajeProgramado();
        v.setEstado(EstadoViaje.EN_TRANSITO);
        when(viajeRepository.findConRutaById(v.getId())).thenReturn(Optional.of(v));

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.eliminar(v.getId(), adminSucursal));
        assertEquals("VIAJE_NO_ELIMINABLE", ex.getCodigo());
        verify(viajeRepository, never()).delete(eq(v));
    }

    @Test
    void eliminarRechazaOtraSucursal() {
        Viaje v = viajeProgramado();
        when(viajeRepository.findConRutaById(v.getId())).thenReturn(Optional.of(v));
        UsuarioAutenticado adminCapital = new UsuarioAutenticado(
                UUID.randomUUID(), "cap@transportes.gt", Rol.ADMIN_SUCURSAL, CAPITAL, "Cap");

        assertThrows(OperacionNoAutorizadaException.class,
                () -> servicio.eliminar(v.getId(), adminCapital));
    }

    // ------------------------------------------------------------------
    // Salida y llegada
    // ------------------------------------------------------------------

    @Test
    void registrarSalidaPasaEnTransitoYPublica() {
        Viaje v = viajeProgramado();
        Requests.SalidaRequest req = new Requests.SalidaRequest(OffsetDateTime.now(), new BigDecimal("123450.00"));
        Viaje enTransito = viajeProgramado();
        enTransito.setEstado(EstadoViaje.EN_TRANSITO);
        when(viajeRepository.findConRutaById(v.getId())).thenReturn(Optional.of(v), Optional.of(enTransito));
        when(registroSalidaRepository.existsByViajeId(v.getId())).thenReturn(false);
        when(registroSalidaRepository.save(any())).thenReturn(null);
        when(viajeRepository.save(any(Viaje.class))).thenReturn(enTransito);

        var resp = servicio.registrarSalida(v.getId(), req, adminSucursal);

        assertEquals(EstadoViaje.EN_TRANSITO, resp.estado());
        verify(eventos).salidaRegistrada(any(), any(), any(), any());
    }

    @Test
    void registrarSalidaRechazaDuplicada() {
        Viaje v = viajeProgramado();
        when(viajeRepository.findConRutaById(v.getId())).thenReturn(Optional.of(v));
        when(registroSalidaRepository.existsByViajeId(v.getId())).thenReturn(true);

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.registrarSalida(v.getId(),
                        new Requests.SalidaRequest(OffsetDateTime.now(), BigDecimal.ONE), adminSucursal));
        assertEquals("SALIDA_YA_REGISTRADA", ex.getCodigo());
    }

    @Test
    void registrarLlegadaCalculaDistanciaYFinaliza() {
        Viaje v = viajeProgramado();
        v.setEstado(EstadoViaje.EN_TRANSITO);
        Requests.LlegadaRequest req = new Requests.LlegadaRequest(
                OffsetDateTime.now(), new BigDecimal("123655.50"), new BigDecimal("420.00"));
        var salida = org.transportsgt.tripservice.models.RegistroSalida.builder()
                .viaje(v).idBus(BUS_1).idChofer(CHOFER_1)
                .fechaHoraSalidaReal(OffsetDateTime.now().minusHours(4))
                .kilometrajeInicial(new BigDecimal("123450.00"))
                .registradoPor(adminSucursal.id()).build();
        Viaje finalizado = viajeProgramado();
        finalizado.setId(v.getId());
        finalizado.setEstado(EstadoViaje.FINALIZADO);
        when(viajeRepository.findConRutaById(v.getId())).thenReturn(Optional.of(v), Optional.of(finalizado));
        when(registroLlegadaRepository.existsByViajeId(v.getId())).thenReturn(false);
        when(registroSalidaRepository.findByViajeId(v.getId())).thenReturn(Optional.of(salida));
        when(choferRefRepository.findById(CHOFER_1)).thenReturn(Optional.of(choferRef()));
        when(busRefRepository.findById(BUS_1)).thenReturn(Optional.of(busActivo()));
        when(registroLlegadaRepository.save(any())).thenReturn(null);
        when(viajeRepository.save(any(Viaje.class))).thenReturn(finalizado);
        var llegadaGuardada = org.transportsgt.tripservice.models.RegistroLlegada.builder()
                .viaje(finalizado)
                .fechaHoraLlegadaReal(req.fechaHoraLlegadaReal())
                .kilometrajeFinal(req.kilometrajeFinal())
                .distanciaRecorridaKm(new BigDecimal("205.50"))
                .gastoCombustibleTotal(req.gastoCombustibleTotal())
                .salarioBaseChofer(new BigDecimal("150.00"))
                .registradoPor(adminSucursal.id())
                .build();
        when(registroLlegadaRepository.findByViajeId(v.getId())).thenReturn(Optional.of(llegadaGuardada));

        var resp = servicio.registrarLlegada(v.getId(), req, adminSucursal);

        assertEquals(EstadoViaje.FINALIZADO, resp.estado());
        assertNotNull(resp.registroLlegada());
        assertEquals(new BigDecimal("205.50"), resp.registroLlegada().distanciaRecorridaKm());
        verify(eventos).llegadaRegistrada(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void registrarLlegadaRechazaKilometrajeMenor() {
        Viaje v = viajeProgramado();
        v.setEstado(EstadoViaje.EN_TRANSITO);
        var salida = org.transportsgt.tripservice.models.RegistroSalida.builder()
                .viaje(v).idBus(BUS_1).idChofer(CHOFER_1)
                .fechaHoraSalidaReal(OffsetDateTime.now().minusHours(4))
                .kilometrajeInicial(new BigDecimal("123450.00"))
                .registradoPor(adminSucursal.id()).build();
        when(viajeRepository.findConRutaById(v.getId())).thenReturn(Optional.of(v));
        when(registroLlegadaRepository.existsByViajeId(v.getId())).thenReturn(false);
        when(registroSalidaRepository.findByViajeId(v.getId())).thenReturn(Optional.of(salida));

        assertThrows(SolicitudInvalidaException.class, () -> servicio.registrarLlegada(v.getId(),
                new Requests.LlegadaRequest(OffsetDateTime.now(), new BigDecimal("100000.00"),
                        BigDecimal.TEN), adminSucursal));
    }

    // ------------------------------------------------------------------
    // Permisos
    // ------------------------------------------------------------------

    @Test
    void choferNoPuedeVerViajeAjeno() {
        Viaje v = viajeProgramado();
        v.setIdChofer(UUID.randomUUID());
        when(viajeRepository.findConRutaById(v.getId())).thenReturn(Optional.of(v));
        when(choferRefRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(OperacionNoAutorizadaException.class,
                () -> servicio.detalle(v.getId(), chofer));
    }

    @Test
    void choferSiPuedeVerSuViaje() {
        Viaje v = viajeProgramado();
        when(viajeRepository.findConRutaById(v.getId())).thenReturn(Optional.of(v));
        when(choferRefRepository.findById(CHOFER_1)).thenReturn(Optional.of(choferRef()));
        when(busRefRepository.findById(BUS_1)).thenReturn(Optional.of(busActivo()));
        when(registroSalidaRepository.findByViajeId(v.getId())).thenReturn(Optional.empty());
        when(registroLlegadaRepository.findByViajeId(v.getId())).thenReturn(Optional.empty());

        var resp = servicio.detalle(v.getId(), chofer);

        assertEquals(v.getId(), resp.id());
    }

    @Test
    void viajeInexistenteLanza404() {
        UUID id = UUID.randomUUID();
        when(viajeRepository.findConRutaById(id)).thenReturn(Optional.empty());

        assertThrows(ViajeNoEncontradoException.class, () -> servicio.detalle(id, adminSistema));
    }

    @Test
    void listarFiltraPorEstadoYTipo() {
        Viaje v1 = viajeProgramado();
        Viaje v2 = viajeProgramado();
        v2.setTipoViaje(TipoViaje.ALQUILER_PRIVADO);
        v2.setEstado(EstadoViaje.PENDIENTE_ASIGNACION);
        when(viajeRepository.findAll()).thenReturn(List.of(v1, v2));
        when(busRefRepository.findById(BUS_1)).thenReturn(Optional.of(busActivo()));
        when(choferRefRepository.findById(CHOFER_1)).thenReturn(Optional.of(choferRef()));
        when(viajeRepository.findConRutaById(any())).thenAnswer(inv ->
                Optional.of(inv.getArgument(0).equals(v1.getId()) ? v1 : v2));
        when(registroSalidaRepository.findByViajeId(any())).thenReturn(Optional.empty());
        when(registroLlegadaRepository.findByViajeId(any())).thenReturn(Optional.empty());

        var soloRegulares = servicio.listar(Optional.of(TipoViaje.REGULAR), Optional.empty(),
                null, null, null, null, Optional.empty(), adminSistema);
        assertEquals(1, soloRegulares.size());
        assertEquals(TipoViaje.REGULAR, soloRegulares.get(0).tipoViaje());

        var pendientes = servicio.listar(Optional.empty(), Optional.empty(),
                null, null, null, null, Optional.of(true), adminSistema);
        assertEquals(1, pendientes.size());
        assertEquals(EstadoViaje.PENDIENTE_ASIGNACION, pendientes.get(0).estado());
    }

    @Test
    void adminSucursalSoloVeLosDeSuSucursal() {
        Viaje vOtro = viajeProgramado();
        vOtro.setIdSucursalOrigen(CAPITAL);
        when(viajeRepository.findAll()).thenReturn(List.of(vOtro));

        var lista = servicio.listar(Optional.empty(), Optional.empty(), null, null, null, null,
                Optional.empty(), adminSucursal);

        assertTrue(lista.isEmpty());
    }

    @Test
    void activosPorBusUsaEstadosProgramadoYEnTransito() {
        Viaje v = viajeProgramado();
        when(viajeRepository.findAllByIdBusAndEstadoInOrderByFechaHoraSalida(eq(BUS_1), any()))
                .thenReturn(List.of(v));
        when(busRefRepository.findById(BUS_1)).thenReturn(Optional.of(busActivo()));
        when(choferRefRepository.findById(CHOFER_1)).thenReturn(Optional.of(choferRef()));
        when(viajeRepository.findConRutaById(v.getId())).thenReturn(Optional.of(v));
        when(registroSalidaRepository.findByViajeId(v.getId())).thenReturn(Optional.empty());
        when(registroLlegadaRepository.findByViajeId(v.getId())).thenReturn(Optional.empty());

        var activos = servicio.activosPorBus(BUS_1);

        assertEquals(1, activos.size());
        verify(viajeRepository).findAllByIdBusAndEstadoInOrderByFechaHoraSalida(eq(BUS_1),
                any(java.util.Set.class));
    }

}
