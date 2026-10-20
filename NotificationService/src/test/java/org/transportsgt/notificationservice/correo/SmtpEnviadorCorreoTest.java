package org.transportsgt.notificationservice.correo;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.transportsgt.notificationservice.fixture.PropiedadesFixture;

import java.io.ByteArrayOutputStream;
import java.net.ConnectException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmtpEnviadorCorreoTest {

    @Mock
    private JavaMailSender mailSender;

    private SmtpEnviadorCorreo enviador;
    private MensajeCorreo mensaje;

    @BeforeEach
    void setUp() {
        enviador = new SmtpEnviadorCorreo(mailSender, new TextoPlano(), PropiedadesFixture.propiedades());
        mensaje = new MensajeCorreo("ana@gmail.com", "Confirmación de compra",
                "<html><body><p>Hola, Ana. Total Q 170.00</p></body></html>");
    }

    @Nested
    class Enviar {

        @Test
        void enviar_cuandoElServidorAcepta_mandaHtmlConTextoAlternativoDesdeElRemitente() throws Exception {
            // Arrange
            when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
            ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);

            // Act
            enviador.enviar(mensaje);

            // Assert
            verify(mailSender).send(captor.capture());
            MimeMessage enviado = captor.getValue();
            enviado.saveChanges();
            ByteArrayOutputStream crudo = new ByteArrayOutputStream();
            enviado.writeTo(crudo);
            String contenido = crudo.toString();
            assertAll(
                    () -> assertEquals("ana@gmail.com", enviado.getAllRecipients()[0].toString()),
                    () -> assertEquals("no-responder@transportes.gt", ((InternetAddress) enviado.getFrom()[0]).getAddress()),
                    () -> assertEquals("Confirmación de compra", enviado.getSubject()),
                    () -> assertTrue(enviado.getContent() instanceof MimeMultipart),
                    () -> assertTrue(contenido.contains("text/plain")),
                    () -> assertTrue(contenido.contains("text/html"))
            );
        }

        @Test
        void enviar_cuandoElServidorFalla_lanzaEnvioCorreoConLaCausaRaiz() {
            // Arrange
            when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
            doThrow(new MailSendException("Mail server connection failed", new ConnectException("Connection refused")))
                    .when(mailSender).send(any(MimeMessage.class));

            // Act
            EnvioCorreoException ex = assertThrows(EnvioCorreoException.class, () -> enviador.enviar(mensaje));

            // Assert
            assertAll(
                    () -> assertEquals("ConnectException: Connection refused", ex.getMessage()),
                    () -> assertEquals("CORREO_NO_ENVIADO", ex.getCodigo())
            );
        }

        @Test
        void enviar_cuandoLaCausaNoTraeMensaje_usaSoloElTipo() {
            // Arrange
            when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
            doThrow(new MailSendException((String) null)).when(mailSender).send(any(MimeMessage.class));

            // Act & Assert
            assertEquals("MailSendException", assertThrows(EnvioCorreoException.class, () -> enviador.enviar(mensaje))
                    .getMessage());
        }
    }
}
