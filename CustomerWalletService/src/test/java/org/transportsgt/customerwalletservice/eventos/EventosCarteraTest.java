package org.transportsgt.customerwalletservice.eventos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.customerwalletservice.fixture.CarteraFixture;
import org.transportsgt.customerwalletservice.fixture.ClienteFixture;
import org.transportsgt.customerwalletservice.models.Cartera;
import org.transportsgt.customerwalletservice.models.Cliente;
import org.transportsgt.customerwalletservice.models.MovimientoCartera;
import org.transportsgt.eventos.RegistroOutbox;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosCarteraRecargada;
import org.transportsgt.eventos.payload.DatosCarteraReembolsada;
import org.transportsgt.eventos.payload.DatosPerfilClienteActualizado;
import org.transportsgt.eventos.payload.TipoReferencia;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventosCarteraTest {

    @Mock
    private RegistroOutbox outbox;

    private EventosCartera eventos;
    private Cliente cliente;
    private Cartera cartera;

    @BeforeEach
    void setUp() {
        eventos = new EventosCartera(outbox);
        cliente = ClienteFixture.cliente(UUID.randomUUID());
        cliente.setVersion(3L);
        cartera = CarteraFixture.cartera(cliente, "350.00");
        cartera.setVersion(7L);
    }

    @Nested
    class PerfilClienteActualizado {

        @Test
        void perfilClienteActualizado_registraInstantaneaConIdUsuarioYVersionDelCliente() {
            // Arrange
            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

            // Act
            eventos.perfilClienteActualizado(cliente);

            // Assert
            verify(outbox).registrar(eq(TipoEvento.PERFIL_CLIENTE_ACTUALIZADO), eq(cliente.getIdUsuario()), eq(3L),
                    captor.capture());
            DatosPerfilClienteActualizado datos = (DatosPerfilClienteActualizado) captor.getValue();
            assertAll(
                    () -> assertEquals(cliente.getIdUsuario(), datos.idUsuario()),
                    () -> assertEquals(ClienteFixture.NIT, datos.nit()),
                    () -> assertEquals(ClienteFixture.NOMBRE, datos.nombreCompleto()),
                    () -> assertTrue(datos.activo())
            );
        }
    }

    @Nested
    class CarteraRecargada {

        @Test
        void carteraRecargada_registraMontoSaldoPosteriorYFecha() {
            // Arrange
            MovimientoCartera recarga = CarteraFixture.recarga(cartera, "200.00", "clave");
            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

            // Act
            eventos.carteraRecargada(cartera, recarga);

            // Assert
            verify(outbox).registrar(eq(TipoEvento.CARTERA_RECARGADA), eq(cartera.getId()), eq(7L), captor.capture());
            DatosCarteraRecargada datos = (DatosCarteraRecargada) captor.getValue();
            assertAll(
                    () -> assertEquals(recarga.getId(), datos.idMovimiento()),
                    () -> assertEquals(cartera.getId(), datos.idCartera()),
                    () -> assertEquals(cliente.getIdUsuario(), datos.idUsuario()),
                    () -> assertEquals(new BigDecimal("200.00"), datos.monto()),
                    () -> assertEquals(new BigDecimal("350.00"), datos.saldoPosterior()),
                    () -> assertEquals(CarteraFixture.FECHA, datos.fechaRecarga())
            );
        }
    }

    @Nested
    class CarteraReembolsada {

        @Test
        void carteraReembolsada_registraReferenciaMotivoYSaldoPosterior() {
            // Arrange
            UUID idAlquiler = UUID.randomUUID();
            MovimientoCartera reembolso = CarteraFixture.reembolso(cartera, TipoReferencia.ALQUILER, idAlquiler, "3800.00");
            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

            // Act
            eventos.carteraReembolsada(cartera, reembolso, "VIAJE_ELIMINADO");

            // Assert
            verify(outbox).registrar(eq(TipoEvento.CARTERA_REEMBOLSADA), eq(cartera.getId()), eq(7L), captor.capture());
            DatosCarteraReembolsada datos = (DatosCarteraReembolsada) captor.getValue();
            assertAll(
                    () -> assertEquals(reembolso.getId(), datos.idMovimiento()),
                    () -> assertEquals(new BigDecimal("3800.00"), datos.monto()),
                    () -> assertEquals(new BigDecimal("350.00"), datos.saldoPosterior()),
                    () -> assertEquals(TipoReferencia.ALQUILER, datos.tipoReferencia()),
                    () -> assertEquals(idAlquiler, datos.idReferencia()),
                    () -> assertEquals("VIAJE_ELIMINADO", datos.motivo())
            );
        }
    }
}
