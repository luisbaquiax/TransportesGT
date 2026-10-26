package org.transportsgt.notificationservice.services.envio.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.notificationservice.correo.EnviadorCorreo;
import org.transportsgt.notificationservice.correo.EnvioCorreoException;
import org.transportsgt.notificationservice.correo.MensajeCorreo;
import org.transportsgt.notificationservice.fixture.PropiedadesFixture;
import org.transportsgt.notificationservice.models.EstadoNotificacion;
import org.transportsgt.notificationservice.models.Notificacion;
import org.transportsgt.notificationservice.models.TipoNotificacion;
import org.transportsgt.notificationservice.repositories.NotificacionRepository;
import org.transportsgt.notificationservice.services.envio.ResumenEnvio;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnvioCorreosTest {

    private static final OffsetDateTime AHORA = OffsetDateTime.parse("2026-10-20T10:00-06:00");

    @Nested
    class Politica {

        private final PoliticaReintentos politica = new PoliticaReintentos(PropiedadesFixture.propiedades());

        @Test
        void espera_cuandoAumentanLosIntentos_duplicaDesdeUnMinuto() {
            assertAll(
                    () -> assertEquals(Duration.ofMinutes(1), politica.espera(1)),
                    () -> assertEquals(Duration.ofMinutes(2), politica.espera(2)),
                    () -> assertEquals(Duration.ofMinutes(4), politica.espera(3)),
                    () -> assertEquals(Duration.ofMinutes(8), politica.espera(4)),
                    () -> assertEquals(Duration.ofMinutes(1), politica.espera(0))
            );
        }

        @Test
        void agotado_cuandoLlegaAlQuintoIntento_esVerdadero() {
            assertAll(
                    () -> assertFalse(politica.agotado(4)),
                    () -> assertTrue(politica.agotado(5))
            );
        }

        @Test
        void recortar_cuandoElErrorTraeCorreosOEsLargo_losQuitaYLimitaElLargo() {
            assertAll(
                    () -> assertEquals("550 [correo]: mailbox unavailable",
                            politica.recortar(" 550 ana.lopez@gmail.com: mailbox unavailable ")),
                    () -> assertEquals(PoliticaReintentos.LARGO_MAXIMO_ERROR, politica.recortar("x".repeat(900)).length()),
                    () -> assertEquals("Error desconocido", politica.recortar(null)),
                    () -> assertEquals("Error desconocido", politica.recortar(" "))
            );
        }
    }

    @Nested
    class EnviarSiguiente {

        @Mock
        private NotificacionRepository notificacionRepository;
        @Mock
        private EnviadorCorreo enviador;

        private GestorEnvio gestor;
        private Notificacion pendiente;

        @BeforeEach
        void setUp() {
            gestor = new GestorEnvio(notificacionRepository, enviador,
                    new PoliticaReintentos(PropiedadesFixture.propiedades()), PropiedadesFixture.fechas());
            pendiente = Notificacion.builder().id(UUID.randomUUID()).tipo(TipoNotificacion.COMPRA_BOLETOS)
                    .correoDestino("ana@gmail.com").asunto("Compra").cuerpo("<html/>")
                    .estado(EstadoNotificacion.PENDIENTE).intentos((short) 0).proximoIntentoEn(AHORA)
                    .claveDedup("evt:x:ana@gmail.com").ultimoError("anterior").build();
        }

        @Test
        void enviarSiguiente_cuandoSale_quedaEnviada() {
            // Arrange
            when(notificacionRepository.bloquearSiguientePendiente(AHORA)).thenReturn(Optional.of(pendiente));

            // Act
            Optional<GestorEnvio.Resultado> resultado = gestor.enviarSiguiente();

            // Assert
            verify(enviador).enviar(new MensajeCorreo("ana@gmail.com", "Compra", "<html/>"));
            assertAll(
                    () -> assertEquals(Optional.of(GestorEnvio.Resultado.ENVIADA), resultado),
                    () -> assertEquals(EstadoNotificacion.ENVIADA, pendiente.getEstado()),
                    () -> assertEquals(AHORA, pendiente.getEnviadaEn()),
                    () -> assertEquals((short) 1, pendiente.getIntentos()),
                    () -> assertNull(pendiente.getUltimoError())
            );
        }

        @Test
        void enviarSiguiente_cuandoFalla_reprogramaConEsperaExponencial() {
            // Arrange: ya falló 2 veces; este es el 3.er intento → espera 4 min
            pendiente.setIntentos((short) 2);
            when(notificacionRepository.bloquearSiguientePendiente(AHORA)).thenReturn(Optional.of(pendiente));
            doThrow(new EnvioCorreoException("ConnectException: Connection refused", null)).when(enviador).enviar(any());

            // Act
            Optional<GestorEnvio.Resultado> resultado = gestor.enviarSiguiente();

            // Assert
            assertAll(
                    () -> assertEquals(Optional.of(GestorEnvio.Resultado.REPROGRAMADA), resultado),
                    () -> assertEquals(EstadoNotificacion.PENDIENTE, pendiente.getEstado()),
                    () -> assertEquals((short) 3, pendiente.getIntentos()),
                    () -> assertEquals(AHORA.plusMinutes(4), pendiente.getProximoIntentoEn()),
                    () -> assertEquals("ConnectException: Connection refused", pendiente.getUltimoError())
            );
        }

        @Test
        void enviarSiguiente_cuandoFallaElQuintoIntento_quedaFallida() {
            // Arrange
            pendiente.setIntentos((short) 4);
            when(notificacionRepository.bloquearSiguientePendiente(AHORA)).thenReturn(Optional.of(pendiente));
            doThrow(new EnvioCorreoException("SMTP caído", null)).when(enviador).enviar(any());

            // Act
            Optional<GestorEnvio.Resultado> resultado = gestor.enviarSiguiente();

            // Assert
            assertAll(
                    () -> assertEquals(Optional.of(GestorEnvio.Resultado.FALLIDA), resultado),
                    () -> assertEquals(EstadoNotificacion.FALLIDA, pendiente.getEstado()),
                    () -> assertEquals((short) 5, pendiente.getIntentos())
            );
        }

        @Test
        void enviarSiguiente_cuandoNoHayPendientes_devuelveVacio() {
            // Arrange
            when(notificacionRepository.bloquearSiguientePendiente(AHORA)).thenReturn(Optional.empty());

            // Act & Assert
            assertTrue(gestor.enviarSiguiente().isEmpty());
        }
    }

    @Nested
    class Procesar {

        private GestorEnvio gestor;

        @BeforeEach
        void setUp() {
            gestor = mock(GestorEnvio.class);
        }

        @Test
        void procesar_cuandoSeAcabanLosPendientes_cuentaCadaResultado() {
            // Arrange
            when(gestor.enviarSiguiente()).thenReturn(Optional.of(GestorEnvio.Resultado.ENVIADA),
                    Optional.of(GestorEnvio.Resultado.ENVIADA), Optional.of(GestorEnvio.Resultado.REPROGRAMADA),
                    Optional.of(GestorEnvio.Resultado.FALLIDA), Optional.empty());

            // Act
            ResumenEnvio resumen = new EnvioCorreosServiceImpl(gestor, PropiedadesFixture.propiedades()).procesar();

            // Assert
            assertEquals(new ResumenEnvio(2, 1, 1), resumen);
        }

        @Test
        void procesar_cuandoHayMasQueElLote_seDetieneEnElLote() {
            // Arrange
            when(gestor.enviarSiguiente()).thenReturn(Optional.of(GestorEnvio.Resultado.ENVIADA));

            // Act
            ResumenEnvio resumen = new EnvioCorreosServiceImpl(gestor, PropiedadesFixture.propiedades()).procesar();

            // Assert
            assertEquals(new ResumenEnvio(20, 0, 0), resumen);
            verify(gestor, times(20)).enviarSiguiente();
        }
    }
}
