package org.transportsgt.privaterentalservice.services.solicitud.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.payload.Canal;
import org.transportsgt.privaterentalservice.dto.request.SolicitudAlquilerRequest;
import org.transportsgt.privaterentalservice.dto.request.SolicitudMostradorRequest;
import org.transportsgt.privaterentalservice.dto.response.AlquilerResponse;
import org.transportsgt.privaterentalservice.eventos.EventosAlquiler;
import org.transportsgt.privaterentalservice.exception.OperacionNoAutorizadaException;
import org.transportsgt.privaterentalservice.exception.ReglaNegocioException;
import org.transportsgt.privaterentalservice.exception.SolicitudInvalidaException;
import org.transportsgt.privaterentalservice.mappers.AlquilerMapper;
import org.transportsgt.privaterentalservice.models.Alquiler;
import org.transportsgt.privaterentalservice.models.EstadoAlquiler;
import org.transportsgt.privaterentalservice.repositories.AlquilerRepository;
import org.transportsgt.privaterentalservice.repositories.ClienteRefRepository;
import org.transportsgt.privaterentalservice.repositories.CotizacionAlquilerRepository;
import org.transportsgt.privaterentalservice.repositories.SucursalRefRepository;
import org.transportsgt.privaterentalservice.repositories.TarifaAlquilerRepository;
import org.transportsgt.privaterentalservice.services.autorizacion.AutorizacionAlquiler;
import org.transportsgt.privaterentalservice.services.comun.GuardadoAlquiler;
import org.transportsgt.privaterentalservice.services.comun.NormalizadorNit;
import org.transportsgt.privaterentalservice.soporte.DatosPrueba;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolicitudAlquilerServiceImplTest {

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
    private EventosAlquiler eventos;
    @Mock
    private AlquilerMapper mapper;

    private final DatosPrueba datos = new DatosPrueba();
    private SolicitudAlquilerServiceImpl servicio;
    private EstimadorPrecio estimador;

    @BeforeEach
    void setUp() {
        NormalizadorNit nit = new NormalizadorNit();
        estimador = new EstimadorPrecio(tarifaRepository, new CalculadoraDistancia(1.3), datos.calendario);
        servicio = new SolicitudAlquilerServiceImpl(clienteRepository, cotizacionRepository,
                new ValidadorSolicitud(sucursalRepository, datos.calendario), estimador, nit,
                new AutorizacionAlquiler(clienteRepository, nit), new GuardadoAlquiler(alquilerRepository),
                eventos, mapper);
    }

    private SolicitudAlquilerRequest conDistancia() {
        return new SolicitudAlquilerRequest(datos.idSucursal, "Quetzaltenango", "Ciudad de Guatemala",
                datos.salida, false, null, 40, null, null, null, null, new BigDecimal("200.00"));
    }

    private SolicitudAlquilerRequest conCoordenadas(BigDecimal latOrigen, BigDecimal lonOrigen,
                                                    BigDecimal latDestino, BigDecimal lonDestino) {
        return new SolicitudAlquilerRequest(datos.idSucursal, "X", "Y", datos.salida, false, null, 10,
                latOrigen, lonOrigen, latDestino, lonDestino, null);
    }

    private void guardadoYMapeo() {
        when(sucursalRepository.findById(datos.idSucursal)).thenReturn(Optional.of(datos.sucursal(true)));
        when(tarifaRepository.findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(any()))
                .thenReturn(Optional.of(datos.tarifa()));
        when(alquilerRepository.saveAndFlush(any(Alquiler.class))).thenAnswer(inv -> {
            Alquiler a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            a.setVersion(0L);
            return a;
        });
        when(mapper.toResponse(any(Alquiler.class))).thenAnswer(inv -> datos.respuesta(inv.getArgument(0)));
    }

    // ---------- en línea ----------

    @Test
    void enLineaCalculaPrecioYPublicaConElNitDelPerfil() {
        when(clienteRepository.findById(datos.idCliente)).thenReturn(Optional.of(datos.perfil("1234567-8")));
        guardadoYMapeo();

        AlquilerResponse resp = servicio.solicitarEnLinea(conDistancia(), datos.cliente);

        // 200 km × 10.00 + 1 día × 500.00 = 2500.00
        assertEquals(0, new BigDecimal("2500.00").compareTo(resp.precioEstimado()));
        assertEquals(EstadoAlquiler.SOLICITADO, resp.estado());
        assertEquals("12345678", resp.nitCliente());
        verify(eventos).alquilerSolicitado(any(Alquiler.class));
        verify(cotizacionRepository).save(any());
    }

    @Test
    void enLineaUsaLaTarifaVigenteHoyEnGuatemala() {
        when(clienteRepository.findById(datos.idCliente)).thenReturn(Optional.of(datos.perfil("12345678")));
        guardadoYMapeo();

        servicio.solicitarEnLinea(conDistancia(), datos.cliente);

        verify(tarifaRepository).findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(LocalDate.of(2026, 10, 23));
    }

    @Test
    void enLineaSinPerfilEsPerfilIncompleto() {
        when(clienteRepository.findById(datos.idCliente)).thenReturn(Optional.empty());
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> servicio.solicitarEnLinea(conDistancia(), datos.cliente));
        assertEquals("PERFIL_INCOMPLETO", e.getCodigo());
    }

    @Test
    void enLineaRechazaPerfilSinNit() {
        when(clienteRepository.findById(datos.idCliente)).thenReturn(Optional.of(datos.perfil(null)));
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> servicio.solicitarEnLinea(conDistancia(), datos.cliente));
        assertEquals("PERFIL_INCOMPLETO", e.getCodigo());
    }

    @Test
    void sinDistanciaNiCoordenadasEs400() {
        when(clienteRepository.findById(datos.idCliente)).thenReturn(Optional.of(datos.perfil("12345678")));
        when(sucursalRepository.findById(datos.idSucursal)).thenReturn(Optional.of(datos.sucursal(true)));
        SolicitudAlquilerRequest sinNada = new SolicitudAlquilerRequest(datos.idSucursal, "X", "Y",
                datos.salida, false, null, 10, null, null, null, null, null);
        assertThrows(SolicitudInvalidaException.class, () -> servicio.solicitarEnLinea(sinNada, datos.cliente));
    }

    @Test
    void idaYVueltaSinRetornoEsInvalida() {
        when(clienteRepository.findById(datos.idCliente)).thenReturn(Optional.of(datos.perfil("12345678")));
        when(sucursalRepository.findById(datos.idSucursal)).thenReturn(Optional.of(datos.sucursal(true)));
        SolicitudAlquilerRequest idaVuelta = new SolicitudAlquilerRequest(datos.idSucursal, "X", "Y",
                datos.salida, true, null, 10, null, null, null, null, new BigDecimal("100.00"));
        assertThrows(SolicitudInvalidaException.class, () -> servicio.solicitarEnLinea(idaVuelta, datos.cliente));
    }

    @Test
    void salidaEnElPasadoEsInvalida() {
        when(clienteRepository.findById(datos.idCliente)).thenReturn(Optional.of(datos.perfil("12345678")));
        when(sucursalRepository.findById(datos.idSucursal)).thenReturn(Optional.of(datos.sucursal(true)));
        SolicitudAlquilerRequest pasada = new SolicitudAlquilerRequest(datos.idSucursal, "X", "Y",
                OffsetDateTime.parse("2026-10-22T08:00:00-06:00"), false, null, 10,
                null, null, null, null, new BigDecimal("100.00"));
        assertThrows(SolicitudInvalidaException.class, () -> servicio.solicitarEnLinea(pasada, datos.cliente));
    }

    @Test
    void sucursalInactivaRechaza() {
        when(clienteRepository.findById(datos.idCliente)).thenReturn(Optional.of(datos.perfil("12345678")));
        when(sucursalRepository.findById(datos.idSucursal)).thenReturn(Optional.of(datos.sucursal(false)));
        assertThrows(SolicitudInvalidaException.class, () -> servicio.solicitarEnLinea(conDistancia(), datos.cliente));
    }

    @Test
    void coordenadasIncompletasSon422() {
        when(clienteRepository.findById(datos.idCliente)).thenReturn(Optional.of(datos.perfil("12345678")));
        when(sucursalRepository.findById(datos.idSucursal)).thenReturn(Optional.of(datos.sucursal(true)));
        SolicitudAlquilerRequest req = conCoordenadas(new BigDecimal("14.8"), new BigDecimal("-91.5"), null, null);
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> servicio.solicitarEnLinea(req, datos.cliente));
        assertEquals("COORDENADAS_INCOMPLETAS", e.getCodigo());
        verify(alquilerRepository, never()).saveAndFlush(any());
    }

    @Test
    void latitudFueraDeRangoEs422() {
        when(clienteRepository.findById(datos.idCliente)).thenReturn(Optional.of(datos.perfil("12345678")));
        when(sucursalRepository.findById(datos.idSucursal)).thenReturn(Optional.of(datos.sucursal(true)));
        SolicitudAlquilerRequest req = conCoordenadas(new BigDecimal("95"), new BigDecimal("-91.5"),
                new BigDecimal("14.6"), new BigDecimal("-90.5"));
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> servicio.solicitarEnLinea(req, datos.cliente));
        assertEquals("COORDENADAS_INVALIDAS", e.getCodigo());
    }

    @Test
    void conCoordenadasElBackendCalculaLaDistancia() {
        when(clienteRepository.findById(datos.idCliente)).thenReturn(Optional.of(datos.perfil("12345678")));
        guardadoYMapeo();
        SolicitudAlquilerRequest req = conCoordenadas(new BigDecimal("14.8347"), new BigDecimal("-91.5181"),
                new BigDecimal("14.6427"), new BigDecimal("-90.5133"));

        AlquilerResponse resp = servicio.solicitarEnLinea(req, datos.cliente);

        double km = resp.distanciaEstimadaKm().doubleValue();
        assertEquals(true, km > 130 && km < 160, "distancia " + km);
    }

    // ---------- mostrador ----------

    @Test
    void mostradorSinNitEsConsumidorFinal() {
        guardadoYMapeo();
        AlquilerResponse resp = servicio.solicitarMostrador(
                new SolicitudMostradorRequest(conDistancia(), null, "  Juan Pérez "), datos.cajero);

        assertEquals("CF", resp.nitCliente());
        assertEquals("Juan Pérez", resp.nombreCliente());
        assertEquals(Canal.MOSTRADOR, resp.canal());
        assertNull(resp.idUsuarioCliente());
        verify(eventos).alquilerSolicitado(any(Alquiler.class));
    }

    @Test
    void mostradorNormalizaElNit() {
        guardadoYMapeo();
        AlquilerResponse resp = servicio.solicitarMostrador(
                new SolicitudMostradorRequest(conDistancia(), " 1234567-k ", "Juan"), datos.cajero);
        assertEquals("1234567K", resp.nitCliente());
    }

    @Test
    void mostradorConNitInvalidoEs400() {
        when(sucursalRepository.findById(datos.idSucursal)).thenReturn(Optional.of(datos.sucursal(true)));
        SolicitudInvalidaException e = assertThrows(SolicitudInvalidaException.class,
                () -> servicio.solicitarMostrador(
                        new SolicitudMostradorRequest(conDistancia(), "12.345", "Juan"), datos.cajero));
        assertEquals("NIT_INVALIDO", e.getCodigo());
    }

    @Test
    void mostradorEnOtraSucursalUsaLaDelToken() {
        guardadoYMapeo();
        SolicitudAlquilerRequest otra = new SolicitudAlquilerRequest(UUID.randomUUID(), "X", "Y",
                datos.salida, false, null, 10, null, null, null, null, new BigDecimal("50.00"));
        ArgumentCaptor<Alquiler> captor = ArgumentCaptor.forClass(Alquiler.class);

        servicio.solicitarMostrador(new SolicitudMostradorRequest(otra, null, "Juan"), datos.cajero);

        verify(alquilerRepository).saveAndFlush(captor.capture());
        assertEquals(datos.idSucursal, captor.getValue().getIdSucursalOrigen());
        verify(sucursalRepository).findById(eq(datos.idSucursal));
    }

    @Test
    void mostradorProhibidoParaCliente() {
        assertThrows(OperacionNoAutorizadaException.class, () -> servicio.solicitarMostrador(
                new SolicitudMostradorRequest(conDistancia(), "123", "Juan"), datos.cliente));
    }

    // ---------- días del precio ----------

    @Test
    void soloIdaCuentaUnDia() {
        assertEquals(1, estimador.dias(false, datos.salida, null));
    }

    @Test
    void idaYVueltaCuentaDiasEntreMesesIncluidos() {
        // 10 nov → 12 dic: 33 días calendario (antes Period.getDays() daba 3)
        assertEquals(33, estimador.dias(true, datos.salida, OffsetDateTime.parse("2026-12-12T18:00:00-06:00")));
    }

    @Test
    void idaYVueltaCuentaDiasDeGuatemalaNoDeUtc() {
        // 23:30 y 00:30 de Guatemala: dos días distintos aunque en UTC sean el mismo
        assertEquals(2, estimador.dias(true, OffsetDateTime.parse("2026-11-10T23:30:00-06:00"),
                OffsetDateTime.parse("2026-11-11T00:30:00-06:00")));
    }
}
