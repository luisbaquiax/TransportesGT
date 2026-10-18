package org.transportsgt.privaterentalservice.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.transportsgt.eventos.payload.Canal;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.privaterentalservice.clients.CarteraClient;
import org.transportsgt.privaterentalservice.config.security.UsuarioAutenticado;
import org.transportsgt.privaterentalservice.dto.request.CotizacionRequest;
import org.transportsgt.privaterentalservice.dto.request.PagoAlquilerRequest;
import org.transportsgt.privaterentalservice.dto.request.SolicitudAlquilerRequest;
import org.transportsgt.privaterentalservice.dto.request.SolicitudMostradorRequest;
import org.transportsgt.privaterentalservice.dto.response.AlquilerResponse;
import org.transportsgt.privaterentalservice.eventos.EventosAlquiler;
import org.transportsgt.privaterentalservice.exception.AlquilerNoEncontradoException;
import org.transportsgt.privaterentalservice.exception.OperacionNoAutorizadaException;
import org.transportsgt.privaterentalservice.exception.ReglaNegocioException;
import org.transportsgt.privaterentalservice.exception.SolicitudInvalidaException;
import org.transportsgt.privaterentalservice.exception.TransicionInvalidaException;
import org.transportsgt.privaterentalservice.mappers.AlquilerMapper;
import org.transportsgt.privaterentalservice.models.Alquiler;
import org.transportsgt.privaterentalservice.models.ClienteRef;
import org.transportsgt.privaterentalservice.models.EstadoAlquiler;
import org.transportsgt.privaterentalservice.models.SucursalRef;
import org.transportsgt.privaterentalservice.models.TarifaAlquiler;
import org.transportsgt.privaterentalservice.repositories.AlquilerRepository;
import org.transportsgt.privaterentalservice.repositories.ClienteRefRepository;
import org.transportsgt.privaterentalservice.repositories.CotizacionAlquilerRepository;
import org.transportsgt.privaterentalservice.repositories.SucursalRefRepository;
import org.transportsgt.privaterentalservice.repositories.TarifaAlquilerRepository;
import org.transportsgt.privaterentalservice.services.CalculadoraDistancia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlquilerServiceImplTest {

    @Mock
    private AlquilerRepository alquilerRepository;
    @Mock
    private CotizacionAlquilerRepository cotizacionRepository;
    @Mock
    private TarifaAlquilerRepository tarifaRepository;
    @Mock
    private SucursalRefRepository sucursalRepository;
    @Mock
    private ClienteRefRepository clienteRepository;
    @Mock
    private CalculadoraDistancia calculadora;
    @Mock
    private CarteraClient carteraClient;
    @Mock
    private EventosAlquiler eventos;
    @Mock
    private AlquilerMapper mapper;

    private AlquilerServiceImpl servicio;

    private final UUID idSucursal = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private final UUID idCliente = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private final UsuarioAutenticado cliente =
            new UsuarioAutenticado(idCliente, "cliente@transportes.gt", Rol.CLIENTE, null, "Cliente");
    private final UsuarioAutenticado adminSucursal =
            new UsuarioAutenticado(UUID.randomUUID(), "admin.xela@transportes.gt",
                    Rol.ADMIN_SUCURSAL, idSucursal, "Admin Xela");
    private final UsuarioAutenticado adminSistema =
            new UsuarioAutenticado(UUID.randomUUID(), "admin@transportes.gt",
                    Rol.ADMIN_SISTEMA, null, "Admin Sistema");
    private final UsuarioAutenticado cajero =
            new UsuarioAutenticado(UUID.randomUUID(), "cajero@transportes.gt",
                    Rol.CAJERO, idSucursal, "Cajero");

    @BeforeEach
    void setUp() {
        servicio = new AlquilerServiceImpl(alquilerRepository, cotizacionRepository, tarifaRepository,
                sucursalRepository, clienteRepository, calculadora, carteraClient, eventos, mapper);
    }

    private SucursalRef sucursalActiva() {
        return SucursalRef.builder().id(idSucursal).nombre("Quetzaltenango").activa(true).version(0L).build();
    }

    /** Respuesta completa (33 campos) para stubs del mapper. */
    private AlquilerResponse respuesta(Alquiler a) {
        return new AlquilerResponse(a.getId(), a.getCanal(), a.getEstado(), a.getIdUsuarioCliente(),
                a.getNombreCliente(), a.getNitCliente(), a.getIdSucursalOrigen(), a.getOrigen(),
                a.getDestino(), a.getFechaHoraSalida(), a.getFechaHoraRetorno(),
                a.getFechaHoraLlegadaEstimada(), a.getIdaYVuelta(), a.getNumeroPasajeros(),
                a.getLatitudOrigen(), a.getLongitudOrigen(), a.getLatitudDestino(),
                a.getLongitudDestino(), a.getDistanciaEstimadaKm(), a.getPrecioEstimado(),
                a.getPrecioFinal(), a.getIdPago(), a.getFechaPago(), a.getIdViaje(), a.getIdBus(),
                a.getPlacaBus(), a.getIdChofer(), a.getNombreChofer(), a.getMotivoCancelacion(),
                a.getCanceladoEn(), a.getVersion(), a.getCreadoEn(), List.of());
    }

    private TarifaAlquiler tarifa() {
        return TarifaAlquiler.builder()
                .precioPorKm(new BigDecimal("10.00"))
                .precioPorDia(new BigDecimal("500.00"))
                .vigenteDesde(LocalDate.of(2026, 1, 1))
                .creadoPor(UUID.randomUUID())
                .build();
    }

    private SolicitudAlquilerRequest solicitudConDistancia() {
        return new SolicitudAlquilerRequest(idSucursal, "Quetzaltenango", "Ciudad de Guatemala",
                OffsetDateTime.now().plusDays(10), false, null, 40,
                null, null, null, null, new BigDecimal("200.00"));
    }

    private Alquiler alquilerConEstado(EstadoAlquiler estado) {
        return Alquiler.builder()
                .id(UUID.randomUUID())
                .canal(Canal.EN_LINEA)
                .estado(estado)
                .idUsuarioCliente(idCliente)
                .nombreCliente("Transportes Ejemplo")
                .nitCliente("1234567-8")
                .idSucursalOrigen(idSucursal)
                .origen("Quetzaltenango")
                .destino("Ciudad de Guatemala")
                .fechaHoraSalida(OffsetDateTime.now().plusDays(10))
                .idaYVuelta(false)
                .numeroPasajeros((short) 40)
                .distanciaEstimadaKm(new BigDecimal("200.00"))
                .precioEstimado(new BigDecimal("2500.00"))
                .precioFinal(new BigDecimal("2700.00"))
                .fechaHoraLlegadaEstimada(OffsetDateTime.now().plusDays(10).plusHours(6))
                .version(1L)
                .build();
    }

    // ---------- solicitarEnLinea ----------

    @Test
    void solicitarEnLineaCalculaPrecioYPublica() {
        when(clienteRepository.findById(idCliente)).thenReturn(Optional.of(
                ClienteRef.builder().idUsuario(idCliente).nit("1234567-8")
                        .nombreCompleto("Transportes Ejemplo").activo(true).version(0L).build()));
        when(sucursalRepository.findById(idSucursal)).thenReturn(Optional.of(sucursalActiva()));
        when(tarifaRepository.findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(any()))
                .thenReturn(Optional.of(tarifa()));
        when(alquilerRepository.save(any(Alquiler.class))).thenAnswer(inv -> {
            Alquiler a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            a.setVersion(0L);
            return a;
        });
        when(mapper.toResponse(any(Alquiler.class))).thenAnswer(inv -> respuesta(inv.getArgument(0)));

        AlquilerResponse resp = servicio.solicitarEnLinea(solicitudConDistancia(), cliente);

        // 200 km × 10.00 + 1 día × 500.00 = 2500.00
        assertEquals(0, new BigDecimal("2500.00").compareTo(resp.precioEstimado()));
        assertEquals(EstadoAlquiler.SOLICITADO, resp.estado());
        verify(eventos).alquilerSolicitado(any(Alquiler.class));
        verify(cotizacionRepository).save(any());
    }

    @Test
    void solicitarEnLineaRechazaPerfilSinNit() {
        when(clienteRepository.findById(idCliente)).thenReturn(Optional.of(
                ClienteRef.builder().idUsuario(idCliente).nit(null)
                        .nombreCompleto("Sin NIT").activo(true).version(0L).build()));
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> servicio.solicitarEnLinea(solicitudConDistancia(), cliente));
        assertEquals("PERFIL_INCOMPLETO", e.getCodigo());
    }

    @Test
    void solicitarEnLineaRechazaSinDistanciaNiCoordenadas() {
        when(clienteRepository.findById(idCliente)).thenReturn(Optional.of(
                ClienteRef.builder().idUsuario(idCliente).nit("1234567-8")
                        .nombreCompleto("Transportes Ejemplo").activo(true).version(0L).build()));
        when(sucursalRepository.findById(idSucursal)).thenReturn(Optional.of(sucursalActiva()));
        SolicitudAlquilerRequest sinNada = new SolicitudAlquilerRequest(idSucursal, "X", "Y",
                OffsetDateTime.now().plusDays(5), false, null, 10,
                null, null, null, null, null);
        assertThrows(SolicitudInvalidaException.class,
                () -> servicio.solicitarEnLinea(sinNada, cliente));
    }

    @Test
    void idaYVueltaSinRetornoEsInvalida() {
        when(clienteRepository.findById(idCliente)).thenReturn(Optional.of(
                ClienteRef.builder().idUsuario(idCliente).nit("1234567-8")
                        .nombreCompleto("Transportes Ejemplo").activo(true).version(0L).build()));
        when(sucursalRepository.findById(idSucursal)).thenReturn(Optional.of(sucursalActiva()));
        SolicitudAlquilerRequest idaVuelta = new SolicitudAlquilerRequest(idSucursal, "X", "Y",
                OffsetDateTime.now().plusDays(5), true, null, 10,
                null, null, null, null, new BigDecimal("100.00"));
        assertThrows(SolicitudInvalidaException.class,
                () -> servicio.solicitarEnLinea(idaVuelta, cliente));
    }

    @Test
    void sucursalInactivaRechaza() {
        when(clienteRepository.findById(idCliente)).thenReturn(Optional.of(
                ClienteRef.builder().idUsuario(idCliente).nit("1234567-8")
                        .nombreCompleto("Transportes Ejemplo").activo(true).version(0L).build()));
        when(sucursalRepository.findById(idSucursal)).thenReturn(Optional.of(
                SucursalRef.builder().id(idSucursal).nombre("X").activa(false).version(0L).build()));
        assertThrows(SolicitudInvalidaException.class,
                () -> servicio.solicitarEnLinea(solicitudConDistancia(), cliente));
    }

    // ---------- solicitarMostrador ----------

    @Test
    void solicitarMostradorConCajeroUsaSucursalDelToken() {
        when(sucursalRepository.findById(idSucursal)).thenReturn(Optional.of(sucursalActiva()));
        when(tarifaRepository.findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(any()))
                .thenReturn(Optional.of(tarifa()));
        when(alquilerRepository.save(any(Alquiler.class))).thenAnswer(inv -> {
            Alquiler a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            a.setVersion(0L);
            return a;
        });
        when(mapper.toResponse(any(Alquiler.class))).thenAnswer(inv -> respuesta(inv.getArgument(0)));
        SolicitudMostradorRequest req = new SolicitudMostradorRequest(solicitudConDistancia(),
                null, null);
        AlquilerResponse resp = servicio.solicitarMostrador(req, cajero);

        assertEquals("CF", resp.nitCliente());
        assertEquals(Canal.MOSTRADOR, resp.canal());
        assertNull(resp.idUsuarioCliente());
        verify(eventos).alquilerSolicitado(any(Alquiler.class));
    }

    @Test
    void solicitarMostradorProhibidoParaCliente() {
        SolicitudMostradorRequest req = new SolicitudMostradorRequest(solicitudConDistancia(),
                "123", "Juan");
        assertThrows(OperacionNoAutorizadaException.class,
                () -> servicio.solicitarMostrador(req, cliente));
    }

    // ---------- cotizar ----------

    @Test
    void cotizarCambiaEstadoYPublica() {
        Alquiler a = alquilerConEstado(EstadoAlquiler.SOLICITADO);
        when(alquilerRepository.findById(a.getId())).thenReturn(Optional.of(a));
        when(mapper.toResponse(any(Alquiler.class))).thenAnswer(inv -> respuesta(inv.getArgument(0)));
        CotizacionRequest req = new CotizacionRequest(new BigDecimal("2700.00"),
                OffsetDateTime.now().plusDays(10).plusHours(5), "Incluye peajes");
        AlquilerResponse resp = servicio.cotizar(a.getId(), req, adminSucursal);

        assertEquals(EstadoAlquiler.COTIZADO, resp.estado());
        assertEquals(0, new BigDecimal("2700.00").compareTo(resp.precioFinal()));
        verify(eventos).alquilerCotizado(eq(a), eq(adminSucursal.id()));
    }

    @Test
    void cotizarProhibidoEnConfirmado() {
        Alquiler a = alquilerConEstado(EstadoAlquiler.CONFIRMADO);
        when(alquilerRepository.findById(a.getId())).thenReturn(Optional.of(a));
        CotizacionRequest req = new CotizacionRequest(new BigDecimal("2700.00"),
                OffsetDateTime.now().plusDays(10), null);
        assertThrows(TransicionInvalidaException.class,
                () -> servicio.cotizar(a.getId(), req, adminSucursal));
    }

    @Test
    void cotizarDeOtraSucursalEs403() {
        Alquiler a = alquilerConEstado(EstadoAlquiler.SOLICITADO);
        a.setIdSucursalOrigen(UUID.randomUUID());
        when(alquilerRepository.findById(a.getId())).thenReturn(Optional.of(a));
        CotizacionRequest req = new CotizacionRequest(new BigDecimal("2700.00"),
                OffsetDateTime.now().plusDays(10), null);
        assertThrows(OperacionNoAutorizadaException.class,
                () -> servicio.cotizar(a.getId(), req, adminSucursal));
    }

    // ---------- pagarEnLinea ----------

    @Test
    void pagarEnLineaConfirmaYPublica() {
        Alquiler a = alquilerConEstado(EstadoAlquiler.COTIZADO);
        UUID idPago = UUID.randomUUID();
        when(alquilerRepository.findById(a.getId())).thenReturn(Optional.of(a));
        when(carteraClient.pagar(anyString(), eq(a.getId()), any(), any(), any())).thenReturn(idPago);
        when(mapper.toResponse(any(Alquiler.class))).thenAnswer(inv -> respuesta(inv.getArgument(0)));

        PagoAlquilerRequest req = new PagoAlquilerRequest(LocalDate.now());
        AlquilerResponse resp = servicio.pagarEnLinea(a.getId(), req, cliente, "Bearer xyz");

        assertEquals(EstadoAlquiler.CONFIRMADO, resp.estado());
        assertEquals(idPago, resp.idPago());
        verify(eventos).alquilerConfirmado(a);
    }

    @Test
    void pagarEnLineaProhibidoParaOtroUsuario() {
        Alquiler a = alquilerConEstado(EstadoAlquiler.COTIZADO);
        when(alquilerRepository.findById(a.getId())).thenReturn(Optional.of(a));
        UsuarioAutenticado otro = new UsuarioAutenticado(UUID.randomUUID(), "otro@x",
                Rol.CLIENTE, null, "Otro");
        assertThrows(OperacionNoAutorizadaException.class,
                () -> servicio.pagarEnLinea(a.getId(), new PagoAlquilerRequest(LocalDate.now()),
                        otro, "Bearer xyz"));
        verify(carteraClient, never()).pagar(any(), any(), any(), any(), any());
    }

    @Test
    void pagarEnLineaRequiereCotizado() {
        Alquiler a = alquilerConEstado(EstadoAlquiler.SOLICITADO);
        when(alquilerRepository.findById(a.getId())).thenReturn(Optional.of(a));
        assertThrows(TransicionInvalidaException.class,
                () -> servicio.pagarEnLinea(a.getId(), new PagoAlquilerRequest(LocalDate.now()),
                        cliente, "Bearer xyz"));
    }

    // ---------- pagarMostrador ----------

    @Test
    void pagarMostradorConfirmaSinIdPago() {
        Alquiler a = alquilerConEstado(EstadoAlquiler.COTIZADO);
        a.setCanal(Canal.MOSTRADOR);
        when(alquilerRepository.findById(a.getId())).thenReturn(Optional.of(a));
        when(mapper.toResponse(any(Alquiler.class))).thenAnswer(inv -> respuesta(inv.getArgument(0)));
        AlquilerResponse resp = servicio.pagarMostrador(a.getId(),
                new PagoAlquilerRequest(LocalDate.now()), adminSucursal);
        assertEquals(EstadoAlquiler.CONFIRMADO, resp.estado());
        assertNull(resp.idPago());
        verify(eventos).alquilerConfirmado(a);
    }

    @Test
    void pagarMostradorProhibidoParaCliente() {
        assertThrows(OperacionNoAutorizadaException.class,
                () -> servicio.pagarMostrador(UUID.randomUUID(),
                        new PagoAlquilerRequest(LocalDate.now()), cliente));
    }

    // ---------- detalle / historial ----------

    @Test
    void detalleDelClienteSoloPropio() {
        Alquiler a = alquilerConEstado(EstadoAlquiler.SOLICITADO);
        when(alquilerRepository.findById(a.getId())).thenReturn(Optional.of(a));
        when(cotizacionRepository.findAllByAlquilerIdOrderByCreadoEn(a.getId())).thenReturn(List.of());
        when(mapper.toResponse(any(Alquiler.class))).thenAnswer(inv -> respuesta(inv.getArgument(0)));
        assertEquals(a.getId(), servicio.detalle(a.getId(), cliente).id());
    }

    @Test
    void detalleDeOtroClienteEs403() {
        Alquiler a = alquilerConEstado(EstadoAlquiler.SOLICITADO);
        when(alquilerRepository.findById(a.getId())).thenReturn(Optional.of(a));
        UsuarioAutenticado otro = new UsuarioAutenticado(UUID.randomUUID(), "otro@x",
                Rol.CLIENTE, null, "Otro");
        assertThrows(OperacionNoAutorizadaException.class,
                () -> servicio.detalle(a.getId(), otro));
    }

    @Test
    void detalleInexistenteEs404() {
        UUID id = UUID.randomUUID();
        when(alquilerRepository.findById(id)).thenReturn(Optional.empty());
        assertThrows(AlquilerNoEncontradoException.class, () -> servicio.detalle(id, adminSistema));
    }

    @Test
    void miosUsaNitDelPerfil() {
        when(clienteRepository.findById(idCliente)).thenReturn(Optional.of(
                ClienteRef.builder().idUsuario(idCliente).nit("1234567-8")
                        .nombreCompleto("X").activo(true).version(0L).build()));
        when(alquilerRepository.findHistorial(eq(idCliente), eq("1234567-8"), any()))
                .thenReturn(new PageImpl<>(List.of()));
        var page = servicio.mios(cliente, PageRequest.of(0, 20));
        assertEquals(0, page.getTotalElements());
    }

    // ---------- listar ----------

    @Test
    void listarSinAsignarFiltraConfirmadosSinViaje() {
        when(alquilerRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));
        servicio.listar(null, null, null, true, adminSistema, PageRequest.of(0, 20));
        verify(alquilerRepository).findAll(any(Specification.class), any(PageRequest.class));
    }

    @Test
    void listarAdminSucursalAplicaFiltroDeSucursal() {
        when(alquilerRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));
        servicio.listar(EstadoAlquiler.SOLICITADO, null, null, null,
                adminSucursal, PageRequest.of(0, 20));
        verify(alquilerRepository).findAll(any(Specification.class), any(PageRequest.class));
    }
}
