package org.transportsgt.customerwalletservice.eventos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.customerwalletservice.fixture.EventoFixture;
import org.transportsgt.customerwalletservice.services.ReembolsoService;
import org.transportsgt.eventos.EventoNoRecuperableException;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.Canal;
import org.transportsgt.eventos.payload.DatosAlquilerCancelado;
import org.transportsgt.eventos.payload.DatosBoletosAnulados;
import org.transportsgt.eventos.payload.TipoReferencia;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManejadoresReembolsoTest {

    @Mock
    private ReembolsoService reembolsoService;

    private ManejadorBoletosAnulados boletos;
    private ManejadorAlquilerCancelado alquiler;
    private UUID idReferencia;
    private UUID idPago;

    @BeforeEach
    void setUp() {
        boletos = new ManejadorBoletosAnulados(reembolsoService);
        alquiler = new ManejadorAlquilerCancelado(reembolsoService);
        idReferencia = UUID.randomUUID();
        idPago = UUID.randomUUID();
    }

    @Nested
    class Tipo {

        @Test
        void tipo_devuelveLosEventosQueReembolsan() {
            assertAll(
                    () -> assertEquals(TipoEvento.BOLETOS_ANULADOS, boletos.tipo()),
                    () -> assertEquals(TipoEvento.ALQUILER_CANCELADO, alquiler.tipo())
            );
        }
    }

    @Nested
    class ManejarBoletosAnulados {

        @Test
        void manejar_cuandoLaCompraFueEnLinea_reembolsaConLaFechaDelEventoEnGuatemala() {
            // Arrange
            SobreEvento<DatosBoletosAnulados> evento = EventoFixture.sobre(TipoEvento.BOLETOS_ANULADOS,
                    EventoFixture.boletosAnulados(idReferencia, Canal.EN_LINEA, idPago), null);
            when(reembolsoService.reembolsar(TipoReferencia.BOLETOS, idReferencia, idPago,
                    EventoFixture.FECHA_GUATEMALA, EventoFixture.MOTIVO)).thenReturn(true);

            // Act
            boletos.manejar(evento);

            // Assert
            verify(reembolsoService).reembolsar(TipoReferencia.BOLETOS, idReferencia, idPago,
                    EventoFixture.FECHA_GUATEMALA, EventoFixture.MOTIVO);
        }

        @Test
        void manejar_cuandoLaCompraFueEnMostrador_noReembolsa() {
            // Arrange
            SobreEvento<DatosBoletosAnulados> evento = EventoFixture.sobre(TipoEvento.BOLETOS_ANULADOS,
                    EventoFixture.boletosAnulados(idReferencia, Canal.MOSTRADOR, null), null);

            // Act
            boletos.manejar(evento);

            // Assert
            verifyNoInteractions(reembolsoService);
        }

        @Test
        @SuppressWarnings("unchecked")
        void manejar_cuandoFaltaIdCompraUOcurridoEn_lanzaEventoNoRecuperable() {
            // Arrange
            SobreEvento<DatosBoletosAnulados> sinCompra = EventoFixture.sobre(TipoEvento.BOLETOS_ANULADOS,
                    EventoFixture.boletosAnulados(null, Canal.EN_LINEA, idPago), null);
            SobreEvento<DatosBoletosAnulados> sinFecha = (SobreEvento<DatosBoletosAnulados>) (SobreEvento<?>)
                    EventoFixture.sinFecha(TipoEvento.BOLETOS_ANULADOS,
                            EventoFixture.boletosAnulados(idReferencia, Canal.EN_LINEA, idPago));

            // Act & Assert
            assertAll(
                    () -> assertThrows(EventoNoRecuperableException.class, () -> boletos.manejar(sinCompra)),
                    () -> assertThrows(EventoNoRecuperableException.class, () -> boletos.manejar(sinFecha))
            );
            verifyNoInteractions(reembolsoService);
        }
    }

    @Nested
    class ManejarAlquilerCancelado {

        @Test
        void manejar_cuandoSePagoConCartera_reembolsaConLaFechaDeCancelacion() {
            // Arrange
            Instant cancelado = Instant.parse("2026-10-23T03:00:00Z");
            SobreEvento<DatosAlquilerCancelado> evento = EventoFixture.sobre(TipoEvento.ALQUILER_CANCELADO,
                    EventoFixture.alquilerCancelado(idReferencia, idPago, cancelado), null);
            LocalDate fecha = LocalDate.parse("2026-10-22");
            when(reembolsoService.reembolsar(TipoReferencia.ALQUILER, idReferencia, idPago, fecha,
                    EventoFixture.MOTIVO)).thenReturn(true);

            // Act
            alquiler.manejar(evento);

            // Assert
            verify(reembolsoService).reembolsar(TipoReferencia.ALQUILER, idReferencia, idPago, fecha,
                    EventoFixture.MOTIVO);
        }

        @Test
        void manejar_cuandoNoTraeCanceladoEn_usaOcurridoEn() {
            // Arrange
            SobreEvento<DatosAlquilerCancelado> evento = EventoFixture.sobre(TipoEvento.ALQUILER_CANCELADO,
                    EventoFixture.alquilerCancelado(idReferencia, idPago, null), null);
            when(reembolsoService.reembolsar(TipoReferencia.ALQUILER, idReferencia, idPago,
                    EventoFixture.FECHA_GUATEMALA, EventoFixture.MOTIVO)).thenReturn(false);

            // Act
            alquiler.manejar(evento);

            // Assert
            verify(reembolsoService).reembolsar(TipoReferencia.ALQUILER, idReferencia, idPago,
                    EventoFixture.FECHA_GUATEMALA, EventoFixture.MOTIVO);
        }

        @Test
        void manejar_cuandoSePagoEnMostrador_noReembolsa() {
            // Arrange
            SobreEvento<DatosAlquilerCancelado> evento = EventoFixture.sobre(TipoEvento.ALQUILER_CANCELADO,
                    EventoFixture.alquilerCancelado(idReferencia, null, EventoFixture.OCURRIDO_EN), null);

            // Act
            alquiler.manejar(evento);

            // Assert
            verifyNoInteractions(reembolsoService);
        }

        @Test
        @SuppressWarnings("unchecked")
        void manejar_cuandoFaltaIdAlquilerOLaFecha_lanzaEventoNoRecuperable() {
            // Arrange
            SobreEvento<DatosAlquilerCancelado> sinAlquiler = EventoFixture.sobre(TipoEvento.ALQUILER_CANCELADO,
                    EventoFixture.alquilerCancelado(null, idPago, EventoFixture.OCURRIDO_EN), null);
            SobreEvento<DatosAlquilerCancelado> sinFechas = (SobreEvento<DatosAlquilerCancelado>) (SobreEvento<?>)
                    EventoFixture.sinFecha(TipoEvento.ALQUILER_CANCELADO,
                            EventoFixture.alquilerCancelado(idReferencia, idPago, null));

            // Act & Assert
            assertAll(
                    () -> assertThrows(EventoNoRecuperableException.class, () -> alquiler.manejar(sinAlquiler)),
                    () -> assertThrows(EventoNoRecuperableException.class, () -> alquiler.manejar(sinFechas))
            );
            verifyNoInteractions(reembolsoService);
        }
    }
}
