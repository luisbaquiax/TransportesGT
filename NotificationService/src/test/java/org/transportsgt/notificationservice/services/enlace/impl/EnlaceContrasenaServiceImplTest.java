package org.transportsgt.notificationservice.services.enlace.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.payload.identidad.DatosEnlaceContrasena;
import org.transportsgt.eventos.payload.identidad.PropositoContrasena;
import org.transportsgt.notificationservice.correo.CorreoRenderizado;
import org.transportsgt.notificationservice.correo.EnviadorCorreo;
import org.transportsgt.notificationservice.correo.EnvioCorreoException;
import org.transportsgt.notificationservice.correo.MensajeCorreo;
import org.transportsgt.notificationservice.correo.RenderizadorCorreo;
import org.transportsgt.notificationservice.fixture.EventoFixture;
import org.transportsgt.notificationservice.fixture.PropiedadesFixture;
import org.transportsgt.notificationservice.models.EstadoNotificacion;
import org.transportsgt.notificationservice.models.Notificacion;
import org.transportsgt.notificationservice.models.TipoNotificacion;
import org.transportsgt.notificationservice.repositories.NotificacionRepository;
import org.transportsgt.notificationservice.services.bandeja.ClaveDedup;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnlaceContrasenaServiceImplTest {

    @Mock
    private NotificacionRepository notificacionRepository;
    @Mock
    private RenderizadorCorreo renderizador;
    @Mock
    private EnviadorCorreo enviador;

    private EnlaceContrasenaServiceImpl servicio;
    private UUID idEvento;
    private DatosEnlaceContrasena datos;
    private String clave;

    @BeforeEach
    void setUp() {
        servicio = new EnlaceContrasenaServiceImpl(notificacionRepository, new ClaveDedup(),
                new ConstructorEnlaces(PropiedadesFixture.propiedades()), renderizador, enviador,
                PropiedadesFixture.fechas());
        idEvento = UUID.randomUUID();
        datos = EventoFixture.enlace(PropositoContrasena.RESTABLECIMIENTO);
        clave = "evt:" + idEvento + ":ana.lopez@gmail.com";
    }

    @Nested
    class Enviar {

        @Test
        void enviar_cuandoEsNuevo_mandaElEnlaceYGuardaEnviadaSinElCuerpo() {
            // Arrange
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.forClass(Map.class);
            when(notificacionRepository.findByClaveDedup(clave)).thenReturn(Optional.empty());
            when(renderizador.renderizar(eq(TipoNotificacion.ENLACE_CONTRASENA), variables.capture()))
                    .thenReturn(new CorreoRenderizado("Restablece tu contraseña", "<html>enlace</html>"));
            when(notificacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            ArgumentCaptor<Notificacion> guardada = ArgumentCaptor.forClass(Notificacion.class);

            // Act
            boolean enviado = servicio.enviar(idEvento, datos);

            // Assert
            assertTrue(enviado);
            verify(enviador).enviar(new MensajeCorreo("ana.lopez@gmail.com", "Restablece tu contraseña", "<html>enlace</html>"));
            verify(notificacionRepository).save(guardada.capture());
            Notificacion fila = guardada.getValue();
            assertAll(
                    () -> assertEquals("http://localhost:5173/restablecer-contrasena?token=tok-secreto-123",
                            variables.getValue().get("enlace")),
                    () -> assertEquals(false, variables.getValue().get("activacion")),
                    () -> assertEquals(datos.expiraEn(), variables.getValue().get("expiraEn")),
                    () -> assertEquals(EnlaceContrasenaServiceImpl.CUERPO_NO_ALMACENADO, fila.getCuerpo()),
                    () -> assertEquals(EstadoNotificacion.ENVIADA, fila.getEstado()),
                    () -> assertEquals(TipoNotificacion.ENLACE_CONTRASENA, fila.getTipo()),
                    () -> assertEquals(clave, fila.getClaveDedup()),
                    () -> assertEquals(idEvento, fila.getIdEventoOrigen()),
                    () -> assertEquals((short) 1, fila.getIntentos()),
                    () -> assertEquals(OffsetDateTime.parse("2026-10-20T10:00-06:00"), fila.getEnviadaEn()),
                    () -> assertFalse(fila.getCuerpo().contains(EventoFixture.TOKEN))
            );
        }

        @Test
        void enviar_cuandoYaSeEnvio_noReenvia() {
            // Arrange
            when(notificacionRepository.findByClaveDedup(clave)).thenReturn(Optional.of(Notificacion.builder()
                    .estado(EstadoNotificacion.ENVIADA).build()));

            // Act & Assert
            assertFalse(servicio.enviar(idEvento, datos));
            verifyNoInteractions(enviador, renderizador);
        }

        @Test
        void enviar_cuandoElSmtpFalla_propagaElErrorSinGuardar() {
            // Arrange
            when(notificacionRepository.findByClaveDedup(clave)).thenReturn(Optional.empty());
            when(renderizador.renderizar(any(), any())).thenReturn(new CorreoRenderizado("A", "<html/>"));
            doThrow(new EnvioCorreoException("ConnectException: Connection refused", null)).when(enviador).enviar(any());

            // Act & Assert
            assertThrows(EnvioCorreoException.class, () -> servicio.enviar(idEvento, datos));
            verify(notificacionRepository, never()).save(any());
        }
    }

    @Nested
    class Construir {

        private final ConstructorEnlaces enlaces = new ConstructorEnlaces(PropiedadesFixture.propiedades());

        @Test
        void construir_cuandoCambiaElProposito_usaSuRutaYCodificaElToken() {
            assertAll(
                    () -> assertEquals("http://localhost:5173/activar-cuenta?token=abc",
                            enlaces.construir(PropositoContrasena.ACTIVACION, "abc")),
                    () -> assertEquals("http://localhost:5173/restablecer-contrasena?token=a%2Bb%2Fc%3D",
                            enlaces.construir(PropositoContrasena.RESTABLECIMIENTO, "a+b/c="))
            );
        }

        @Test
        void construir_cuandoLaUrlNoTerminaEnBarra_noLaRecorta() {
            // Arrange
            var sinBarra = new ConstructorEnlaces(new org.transportsgt.notificationservice.config.NotificacionesProperties("https://app.transportes.gt",
                    "/r", "/a", "x", PropiedadesFixture.propiedades().envio()));

            // Act & Assert
            assertEquals("https://app.transportes.gt/a?token=t", sinBarra.construir(PropositoContrasena.ACTIVACION, "t"));
        }
    }
}
