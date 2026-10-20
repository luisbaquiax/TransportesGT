package org.transportsgt.notificationservice.integracion;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosEnlaceContrasena;
import org.transportsgt.eventos.payload.PropositoContrasena;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EnlaceContrasenaEmitido: envío directo desde el consumidor y cuerpo no almacenado.
 */
class EnlaceContrasenaIT extends IntegracionBase {

    private SobreEvento<DatosEnlaceContrasena> enlace(String correo, String token) {
        DatosEnlaceContrasena datos = new DatosEnlaceContrasena(UUID.randomUUID(), correo, "Ana López",
                PropositoContrasena.RESTABLECIMIENTO, token, Instant.now().plus(30, ChronoUnit.MINUTES));
        return sobre(TipoEvento.ENLACE_CONTRASENA_EMITIDO, datos.idUsuario(), null, datos);
    }

    @Test
    void enlaceContrasena_cuandoLlegaDosVeces_enviaUnCorreoConElEnlaceSinGuardarElCuerpo() throws Exception {
        // Arrange
        String correo = "ana." + UUID.randomUUID().toString().substring(0, 8) + "@gmail.com";
        String token = "tok-" + UUID.randomUUID();
        SobreEvento<DatosEnlaceContrasena> evento = enlace(correo, token);

        // Act: el mismo evento llega dos veces
        publicarYEsperar(TipoEvento.ENLACE_CONTRASENA_EMITIDO, evento);
        publicar(TipoEvento.ENLACE_CONTRASENA_EMITIDO, evento);
        SMTP.waitForIncomingEmail(5_000, 1);

        // Assert: el correo salió sin pasar por el job de envío
        MimeMessage recibido = recibidosPor(correo).getFirst();
        Map<String, Object> fila = jdbc.queryForMap("""
                SELECT tipo, estado, cuerpo, asunto, intentos, enviada_en FROM notificacion WHERE id_evento_origen = ?
                """, evento.idEvento());
        assertAll(
                () -> assertEquals("Restablece tu contraseña", recibido.getSubject()),
                () -> assertTrue(contenido(recibido).contains(
                        "https://app.transportes.gt/restablecer-contrasena?token=" + token)),
                () -> assertEquals("ENLACE_CONTRASENA", fila.get("tipo")),
                () -> assertEquals("ENVIADA", fila.get("estado")),
                () -> assertEquals("[contenido sensible no almacenado]", fila.get("cuerpo")),
                () -> assertEquals(0, jdbc.queryForObject("""
                        SELECT count(*) FROM notificacion
                         WHERE cuerpo LIKE ? OR asunto LIKE ? OR coalesce(ultimo_error, '') LIKE ?
                        """, Integer.class, "%" + token + "%", "%" + token + "%", "%" + token + "%"))
        );
        Thread.sleep(500);
        assertAll(
                () -> assertEquals(1, recibidosPor(correo).size()),
                () -> assertEquals(1, notificaciones(evento.idEvento()))
        );
    }

    @Test
    void reintentar_unEnlaceDeContrasena_responde409() throws Exception {
        // Arrange
        SobreEvento<DatosEnlaceContrasena> evento = enlace("luis@gmail.com", "tok-" + UUID.randomUUID());
        publicarYEsperar(TipoEvento.ENLACE_CONTRASENA_EMITIDO, evento);
        UUID idNotificacion = jdbc.queryForObject("SELECT id FROM notificacion WHERE id_evento_origen = ?", UUID.class,
                evento.idEvento());
        jdbc.update("UPDATE notificacion SET estado = 'FALLIDA' WHERE id = ?", idNotificacion);

        // Act & Assert
        mockMvc.perform(conToken(post("/notificaciones/" + idNotificacion + "/reintentar"), tokenSistema()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("REINTENTO_NO_PERMITIDO"));
    }
}
