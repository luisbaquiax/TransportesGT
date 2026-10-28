package org.transportsgt.ticketservice.eventos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.ticketservice.fixture.CompraFixture;
import org.transportsgt.ticketservice.fixture.ViajeRefFixture;
import org.transportsgt.ticketservice.models.Compra;
import org.transportsgt.ticketservice.models.ViajeRef;
import org.transportsgt.eventos.mensajeria.publicacion.RegistroOutbox;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.boletos.Canal;
import org.transportsgt.eventos.payload.boletos.DatosBoletosAnulados;
import org.transportsgt.eventos.payload.boletos.DatosVentaBoletos;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventosBoletosTest {

    @Mock
    private RegistroOutbox outbox;

    private EventosBoletos eventos;
    private ViajeRef viaje;

    @BeforeEach
    void setUp() {
        eventos = new EventosBoletos(outbox);
        viaje = ViajeRefFixture.programado(UUID.randomUUID());
    }

    @Test
    void ventaRegistrada_registraLosAsientosOrdenadosYLaVersionDeLaCompra() {
        // Arrange
        Compra compra = CompraFixture.confirmada(UUID.randomUUID(), viaje, UUID.randomUUID(), 14, 3, 9);
        compra.setVersion(1L);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

        // Act
        eventos.ventaRegistrada(compra, viaje.getIdRuta());

        // Assert
        verify(outbox).registrar(eq(TipoEvento.VENTA_BOLETOS_REGISTRADA), eq(compra.getId()), eq(1L), captor.capture());
        DatosVentaBoletos datos = (DatosVentaBoletos) captor.getValue();
        assertAll(
                () -> assertEquals(List.of(3, 9, 14), datos.asientos()),
                () -> assertEquals(viaje.getIdRuta(), datos.idRuta()),
                () -> assertEquals(Canal.EN_LINEA, datos.canal()),
                () -> assertEquals(compra.getIdPago(), datos.idPago()),
                () -> assertEquals(compra.getMontoTotal(), datos.montoTotal()),
                () -> assertEquals(ViajeRefFixture.SUCURSAL_XELA, datos.idSucursalIngreso())
        );
    }

    @Test
    void boletosAnulados_registraElMotivoYElPago() {
        // Arrange
        Compra compra = CompraFixture.anulada(UUID.randomUUID(), viaje, UUID.randomUUID(), 1, 2);
        compra.setIdPago(UUID.randomUUID());
        compra.setVersion(2L);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

        // Act
        eventos.boletosAnulados(compra);

        // Assert
        verify(outbox).registrar(eq(TipoEvento.BOLETOS_ANULADOS), eq(compra.getId()), eq(2L), captor.capture());
        DatosBoletosAnulados datos = (DatosBoletosAnulados) captor.getValue();
        assertAll(
                () -> assertEquals("VIAJE_ELIMINADO", datos.motivo()),
                () -> assertEquals(compra.getIdPago(), datos.idPago()),
                () -> assertEquals(compra.getIdUsuarioComprador(), datos.idUsuarioComprador())
        );
    }
}
