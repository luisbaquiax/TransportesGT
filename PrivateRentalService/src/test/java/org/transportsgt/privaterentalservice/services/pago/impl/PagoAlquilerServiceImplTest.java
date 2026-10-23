package org.transportsgt.privaterentalservice.services.pago.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.transportsgt.privaterentalservice.clients.CarteraClient;
import org.transportsgt.privaterentalservice.dto.request.PagoAlquilerRequest;
import org.transportsgt.privaterentalservice.dto.response.AlquilerResponse;
import org.transportsgt.privaterentalservice.eventos.EventosAlquiler;
import org.transportsgt.privaterentalservice.exception.CarteraRechazoException;
import org.transportsgt.privaterentalservice.exception.OperacionNoAutorizadaException;
import org.transportsgt.privaterentalservice.exception.SolicitudInvalidaException;
import org.transportsgt.privaterentalservice.exception.TransicionInvalidaException;
import org.transportsgt.privaterentalservice.mappers.AlquilerMapper;
import org.transportsgt.privaterentalservice.models.Alquiler;
import org.transportsgt.privaterentalservice.models.EstadoAlquiler;
import org.transportsgt.privaterentalservice.repositories.AlquilerRepository;
import org.transportsgt.privaterentalservice.repositories.ClienteRefRepository;
import org.transportsgt.privaterentalservice.services.autorizacion.AutorizacionAlquiler;
import org.transportsgt.privaterentalservice.services.comun.BuscadorAlquileres;
import org.transportsgt.privaterentalservice.services.comun.GuardadoAlquiler;
import org.transportsgt.privaterentalservice.services.comun.NormalizadorNit;
import org.transportsgt.privaterentalservice.services.comun.TransicionesAlquiler;
import org.transportsgt.privaterentalservice.services.pago.ConfirmadorAlquiler;
import org.transportsgt.privaterentalservice.soporte.DatosPrueba;

import java.time.LocalDate;
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
class PagoAlquilerServiceImplTest {

    @Mock
    private AlquilerRepository alquilerRepository;
    @Mock
    private ClienteRefRepository clienteRepository;
    @Mock
    private CarteraClient carteraClient;
    @Mock
    private EventosAlquiler eventos;
    @Mock
    private AlquilerMapper mapper;

    private final DatosPrueba datos = new DatosPrueba();
    private final PagoAlquilerRequest hoy = new PagoAlquilerRequest(LocalDate.of(2026, 10, 23));
    private PagoAlquilerServiceImpl servicio;

    @BeforeEach
    void setUp() {
        AutorizacionAlquiler autorizacion = new AutorizacionAlquiler(clienteRepository, new NormalizadorNit());
        TransicionesAlquiler transiciones = new TransicionesAlquiler();
        ConfirmadorAlquiler confirmador = new ConfirmadorAlquiler(transiciones,
                new GuardadoAlquiler(alquilerRepository), eventos);
        servicio = new PagoAlquilerServiceImpl(
                List.of(new PagoEnLineaCartera(carteraClient, autorizacion), new PagoEfectivoMostrador(autorizacion)),
                new BuscadorAlquileres(alquilerRepository), transiciones, confirmador, mapper);
    }

    private Alquiler existente(Alquiler a) {
        when(alquilerRepository.findById(a.getId())).thenReturn(Optional.of(a));
        return a;
    }

    private void confirmacion(Alquiler a) {
        when(alquilerRepository.saveAndFlush(a)).thenReturn(a);
        when(mapper.toResponse(any(Alquiler.class))).thenAnswer(inv -> datos.respuesta(inv.getArgument(0)));
    }

    // ---------- en línea (Cartera) ----------

    @Test
    void enLineaCobraEnCarteraConfirmaYPublica() {
        Alquiler a = existente(datos.alquiler(EstadoAlquiler.COTIZADO));
        UUID idPago = UUID.randomUUID();
        when(carteraClient.pagar(eq("Bearer xyz"), eq(a.getId()), any(), eq(hoy.fechaPago()), anyString()))
                .thenReturn(idPago);
        confirmacion(a);

        AlquilerResponse resp = servicio.pagarEnLinea(a.getId(), hoy, datos.cliente, "Bearer xyz");

        assertEquals(EstadoAlquiler.CONFIRMADO, resp.estado());
        assertEquals(idPago, resp.idPago());
        assertEquals(hoy.fechaPago(), resp.fechaPago());
        verify(eventos).alquilerConfirmado(a);
    }

    @Test
    void enLineaProhibidoParaOtroUsuario() {
        Alquiler a = existente(datos.alquiler(EstadoAlquiler.COTIZADO));
        assertThrows(OperacionNoAutorizadaException.class,
                () -> servicio.pagarEnLinea(a.getId(), hoy, datos.otroCliente, "Bearer xyz"));
        verify(carteraClient, never()).pagar(any(), any(), any(), any(), any());
    }

    @Test
    void enLineaRequiereCotizado() {
        Alquiler a = existente(datos.alquiler(EstadoAlquiler.SOLICITADO));
        assertThrows(TransicionInvalidaException.class,
                () -> servicio.pagarEnLinea(a.getId(), hoy, datos.cliente, "Bearer xyz"));
        verify(carteraClient, never()).pagar(any(), any(), any(), any(), any());
    }

    @Test
    void rechazoDeCarteraNoConfirma() {
        Alquiler a = existente(datos.alquiler(EstadoAlquiler.COTIZADO));
        when(carteraClient.pagar(anyString(), eq(a.getId()), any(), any(), anyString()))
                .thenThrow(new CarteraRechazoException(HttpStatus.UNPROCESSABLE_ENTITY, "SALDO_INSUFICIENTE", "Sin saldo"));

        CarteraRechazoException e = assertThrows(CarteraRechazoException.class,
                () -> servicio.pagarEnLinea(a.getId(), hoy, datos.cliente, "Bearer xyz"));

        assertEquals("SALDO_INSUFICIENTE", e.getCodigo());
        assertEquals(EstadoAlquiler.COTIZADO, a.getEstado());
        verify(eventos, never()).alquilerConfirmado(any());
    }

    // ---------- mostrador (efectivo) ----------

    @Test
    void mostradorConfirmaSinIdPago() {
        Alquiler a = existente(datos.alquilerMostrador(EstadoAlquiler.COTIZADO, "CF"));
        confirmacion(a);

        AlquilerResponse resp = servicio.pagarMostrador(a.getId(), hoy, datos.cajero);

        assertEquals(EstadoAlquiler.CONFIRMADO, resp.estado());
        assertNull(resp.idPago());
        verify(eventos).alquilerConfirmado(a);
        verify(carteraClient, never()).pagar(any(), any(), any(), any(), any());
    }

    @Test
    void mostradorProhibidoParaCliente() {
        Alquiler a = existente(datos.alquilerMostrador(EstadoAlquiler.COTIZADO, "CF"));
        assertThrows(OperacionNoAutorizadaException.class,
                () -> servicio.pagarMostrador(a.getId(), hoy, datos.cliente));
    }

    @Test
    void alquilerEnLineaNoSeCobraEnMostrador() {
        Alquiler a = existente(datos.alquiler(EstadoAlquiler.COTIZADO));
        SolicitudInvalidaException e = assertThrows(SolicitudInvalidaException.class,
                () -> servicio.pagarMostrador(a.getId(), hoy, datos.adminSucursal));
        assertEquals("Un alquiler en línea se paga con /pago (cartera)", e.getMessage());
    }

    @Test
    void mostradorDeOtraSucursalEs403() {
        Alquiler a = existente(datos.alquilerMostrador(EstadoAlquiler.COTIZADO, "CF"));
        a.setIdSucursalOrigen(UUID.randomUUID());
        assertThrows(OperacionNoAutorizadaException.class,
                () -> servicio.pagarMostrador(a.getId(), hoy, datos.cajero));
    }
}
