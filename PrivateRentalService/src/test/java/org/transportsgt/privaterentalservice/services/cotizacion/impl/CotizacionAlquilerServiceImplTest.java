package org.transportsgt.privaterentalservice.services.cotizacion.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.privaterentalservice.dto.request.CotizacionRequest;
import org.transportsgt.privaterentalservice.dto.response.AlquilerResponse;
import org.transportsgt.privaterentalservice.eventos.EventosAlquiler;
import org.transportsgt.privaterentalservice.exception.OperacionNoAutorizadaException;
import org.transportsgt.privaterentalservice.exception.SolicitudInvalidaException;
import org.transportsgt.privaterentalservice.exception.TransicionInvalidaException;
import org.transportsgt.privaterentalservice.mappers.AlquilerMapper;
import org.transportsgt.privaterentalservice.models.Alquiler;
import org.transportsgt.privaterentalservice.models.EstadoAlquiler;
import org.transportsgt.privaterentalservice.repositories.AlquilerRepository;
import org.transportsgt.privaterentalservice.repositories.ClienteRefRepository;
import org.transportsgt.privaterentalservice.repositories.CotizacionAlquilerRepository;
import org.transportsgt.privaterentalservice.services.autorizacion.AutorizacionAlquiler;
import org.transportsgt.privaterentalservice.services.comun.BuscadorAlquileres;
import org.transportsgt.privaterentalservice.services.comun.GuardadoAlquiler;
import org.transportsgt.privaterentalservice.services.comun.NormalizadorNit;
import org.transportsgt.privaterentalservice.services.comun.TransicionesAlquiler;
import org.transportsgt.privaterentalservice.soporte.DatosPrueba;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CotizacionAlquilerServiceImplTest {

    @Mock
    private AlquilerRepository alquilerRepository;
    @Mock
    private CotizacionAlquilerRepository cotizacionRepository;
    @Mock
    private ClienteRefRepository clienteRepository;
    @Mock
    private EventosAlquiler eventos;
    @Mock
    private AlquilerMapper mapper;

    private final DatosPrueba datos = new DatosPrueba();
    private CotizacionAlquilerServiceImpl servicio;

    @BeforeEach
    void setUp() {
        servicio = new CotizacionAlquilerServiceImpl(new BuscadorAlquileres(alquilerRepository),
                new AutorizacionAlquiler(clienteRepository, new NormalizadorNit()), new TransicionesAlquiler(),
                new GuardadoAlquiler(alquilerRepository), cotizacionRepository, eventos, mapper);
    }

    private CotizacionRequest cotizacion(OffsetDateTime llegada) {
        return new CotizacionRequest(new BigDecimal("2700.00"), llegada, "Incluye peajes");
    }

    private Alquiler existente(EstadoAlquiler estado) {
        Alquiler a = datos.alquiler(estado);
        when(alquilerRepository.findById(a.getId())).thenReturn(Optional.of(a));
        return a;
    }

    @Test
    void cotizarCambiaEstadoGuardaConFlushYPublica() {
        Alquiler a = existente(EstadoAlquiler.SOLICITADO);
        when(alquilerRepository.saveAndFlush(a)).thenReturn(a);
        when(mapper.toResponse(any(Alquiler.class))).thenAnswer(inv -> datos.respuesta(inv.getArgument(0)));

        AlquilerResponse resp = servicio.cotizar(a.getId(), cotizacion(datos.salida.plusHours(5)), datos.adminSucursal);

        assertEquals(EstadoAlquiler.COTIZADO, resp.estado());
        assertEquals(0, new BigDecimal("2700.00").compareTo(resp.precioFinal()));
        verify(cotizacionRepository).save(any());
        verify(eventos).alquilerCotizado(eq(a), eq(datos.adminSucursal.id()));
    }

    @Test
    void recotizarUnCotizadoSePermite() {
        Alquiler a = existente(EstadoAlquiler.COTIZADO);
        when(alquilerRepository.saveAndFlush(a)).thenReturn(a);
        when(mapper.toResponse(any(Alquiler.class))).thenAnswer(inv -> datos.respuesta(inv.getArgument(0)));
        assertEquals(EstadoAlquiler.COTIZADO,
                servicio.cotizar(a.getId(), cotizacion(datos.salida.plusHours(5)), datos.adminSucursal).estado());
    }

    @Test
    void cotizarProhibidoEnConfirmado() {
        Alquiler a = existente(EstadoAlquiler.CONFIRMADO);
        assertThrows(TransicionInvalidaException.class,
                () -> servicio.cotizar(a.getId(), cotizacion(datos.salida.plusHours(5)), datos.adminSucursal));
    }

    @Test
    void cotizarDeOtraSucursalEs403() {
        Alquiler a = existente(EstadoAlquiler.SOLICITADO);
        a.setIdSucursalOrigen(UUID.randomUUID());
        assertThrows(OperacionNoAutorizadaException.class,
                () -> servicio.cotizar(a.getId(), cotizacion(datos.salida.plusHours(5)), datos.adminSucursal));
    }

    @Test
    void llegadaNoPosteriorALaSalidaEs400() {
        Alquiler a = existente(EstadoAlquiler.SOLICITADO);
        assertThrows(SolicitudInvalidaException.class,
                () -> servicio.cotizar(a.getId(), cotizacion(datos.salida), datos.adminSucursal));
        verify(alquilerRepository, never()).saveAndFlush(any());
    }

    @Test
    void idaYVueltaConLlegadaAntesDelRetornoEs400() {
        Alquiler a = existente(EstadoAlquiler.SOLICITADO);
        a.setIdaYVuelta(true);
        a.setFechaHoraRetorno(datos.salida.plusDays(2));
        assertThrows(SolicitudInvalidaException.class,
                () -> servicio.cotizar(a.getId(), cotizacion(datos.salida.plusDays(1)), datos.adminSucursal));
    }
}
