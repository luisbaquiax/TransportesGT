package org.transportsgt.notificationservice.integracion;

import org.junit.jupiter.api.Test;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosCarteraRecargada;
import org.transportsgt.eventos.payload.DatosUsuario;
import org.transportsgt.eventos.payload.Rol;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Job de envío con el SMTP caído: espera exponencial, FALLIDA y reintento manual desde la bitácora.
 */
class EnvioCorreosIT extends IntegracionBase {

    private Map<String, Object> estado(UUID idNotificacion) {
        return jdbc.queryForMap("""
                SELECT estado, intentos, ultimo_error, proximo_intento_en, now() AS ahora FROM notificacion WHERE id = ?
                """, idNotificacion);
    }

    private static Duration espera(Map<String, Object> fila) {
        return Duration.between(((java.sql.Timestamp) fila.get("ahora")).toInstant(),
                ((java.sql.Timestamp) fila.get("proximo_intento_en")).toInstant());
    }

    @Test
    void enviarPendientes_cuandoElSmtpEstaCaido_reintentaConEsperaQuedaFallidaYSeReintenta() throws Exception {
        // Arrange
        DatosUsuario cliente = contacto(Rol.CLIENTE, null, true);
        UUID idEvento = UUID.randomUUID();
        var evento = new org.transportsgt.eventos.SobreEvento<>(idEvento, TipoEvento.CARTERA_RECARGADA.nombre(), 1,
                java.time.Instant.now(), "servicio-clientes", UUID.randomUUID(), 1L, UUID.randomUUID(),
                new DatosCarteraRecargada(UUID.randomUUID(), UUID.randomUUID(), cliente.idUsuario(),
                        new BigDecimal("50.00"), new BigDecimal("80.00"), LocalDate.parse("2026-10-20")));
        publicarYEsperar(TipoEvento.CARTERA_RECARGADA, evento);
        UUID idNotificacion = jdbc.queryForObject("SELECT id FROM notificacion WHERE id_evento_origen = ?", UUID.class,
                idEvento);
        SMTP.stop();

        // Act & Assert: 4 fallos reprogramados a 1, 2, 4 y 8 minutos
        for (int intento = 1; intento <= 4; intento++) {
            enviarPendientes();
            Map<String, Object> fila = estado(idNotificacion);
            long minutos = 1L << (intento - 1);
            Duration espera = espera(fila);
            int numero = intento;
            assertAll("intento " + intento,
                    () -> assertEquals("PENDIENTE", fila.get("estado")),
                    () -> assertEquals(numero, fila.get("intentos")),
                    () -> assertTrue(espera.compareTo(Duration.ofMinutes(minutos).minusSeconds(10)) > 0
                            && espera.compareTo(Duration.ofMinutes(minutos).plusSeconds(10)) < 0, "espera " + espera),
                    () -> assertTrue(fila.get("ultimo_error").toString().contains("Exception"))
            );
            jdbc.update("UPDATE notificacion SET proximo_intento_en = now() WHERE id = ?", idNotificacion);
        }
        enviarPendientes();
        assertEquals("FALLIDA", estado(idNotificacion).get("estado"));
        assertEquals(5, estado(idNotificacion).get("intentos"));

        // La bitácora lo muestra y el administrador lo reintenta
        String sistema = tokenSistema();
        mockMvc.perform(conToken(get("/notificaciones").param("estado", "FALLIDA").param("tipo", "CARTERA_RECARGADA"),
                        sistema))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[?(@.idNotificacion == '" + idNotificacion + "')].intentos").value(5))
                .andExpect(jsonPath("$.contenido[0].cuerpo").doesNotExist());
        mockMvc.perform(conToken(post("/notificaciones/" + idNotificacion + "/reintentar"), sistema))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.intentos").value(0));

        // Con el SMTP de vuelta, el siguiente envío lo manda
        SMTP.start();
        mockMvc.perform(conToken(post("/notificaciones/interno/procesar"), sistema))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.envio.enviadas").value(1));
        assertAll(
                () -> assertEquals("ENVIADA", estado(idNotificacion).get("estado")),
                () -> assertEquals(1, recibidosPor(cliente.correo()).size())
        );
        mockMvc.perform(conToken(get("/notificaciones/" + idNotificacion), sistema))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuerpo").value(org.hamcrest.Matchers.containsString("Q 80.00")));
    }

    @Test
    void bitacora_sinRolDeSistema_responde403() throws Exception {
        // Act & Assert
        mockMvc.perform(conToken(get("/notificaciones"), token(Rol.ADMIN_SUCURSAL, UUID.randomUUID())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/notificaciones")).andExpect(status().isUnauthorized());
        mockMvc.perform(conToken(post("/notificaciones/" + UUID.randomUUID() + "/reintentar"), tokenSistema()))
                .andExpect(status().isNotFound());
        mockMvc.perform(conToken(get("/notificaciones").param("desde", "2026-10-21").param("hasta", "2026-10-20"),
                        tokenSistema()))
                .andExpect(status().isBadRequest());
    }
}
