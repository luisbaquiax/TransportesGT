package org.transportsgt.customerwalletservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.transportsgt.customerwalletservice.dto.request.RecargaRequest;
import org.transportsgt.eventos.payload.Rol;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecargaIT extends IntegracionBase {

    private static final LocalDate FECHA = LocalDate.parse("2026-10-20");

    @Test
    void recarga_cuandoSeRepiteLaClave_sumaUnaSolaVezYPublicaUnEvento() throws Exception {
        // Arrange
        UUID idUsuario = UUID.randomUUID();
        publicarUsuarioCreado(idUsuario, true);
        String token = token(idUsuario, Rol.CLIENTE);
        String clave = UUID.randomUUID().toString();
        String cuerpo = json(new RecargaRequest(new BigDecimal("200.00"), FECHA));

        // Act
        mockMvc.perform(conToken(post("/cartera/recargas"), token).header(CABECERA_IDEMPOTENCIA, clave)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.monto").value("200.00"))
                .andExpect(jsonPath("$.saldoPosterior").value("200.00"));
        mockMvc.perform(conToken(post("/cartera/recargas"), token).header(CABECERA_IDEMPOTENCIA, clave)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk());

        // Assert
        Map<String, Object> evento = jdbc.queryForMap("""
                SELECT llave_ruteo, datos ->> 'saldoPosterior' AS saldo, datos ->> 'fechaRecarga' AS fecha
                  FROM outbox_evento WHERE tipo_evento = 'CarteraRecargada' AND datos ->> 'idUsuario' = ?
                """, idUsuario.toString());
        assertAll(
                () -> assertEquals(new BigDecimal("200.00"), saldo(idUsuario)),
                () -> assertEquals(1, movimientos(idUsuario, "RECARGA")),
                () -> assertEquals("cartera.cartera.recargada", evento.get("llave_ruteo")),
                () -> assertEquals("200.00", evento.get("saldo")),
                () -> assertEquals("2026-10-20", evento.get("fecha"))
        );
        mockMvc.perform(conToken(get("/cartera/yo/movimientos").param("tipo", "RECARGA"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].tipo").value("RECARGA"));
    }

    @Test
    void recarga_cuandoLaClaveSeRepiteConOtroMonto_responde409() throws Exception {
        // Arrange
        UUID idUsuario = UUID.randomUUID();
        publicarUsuarioCreado(idUsuario, true);
        String token = token(idUsuario, Rol.CLIENTE);
        String clave = UUID.randomUUID().toString();
        mockMvc.perform(conToken(post("/cartera/recargas"), token).header(CABECERA_IDEMPOTENCIA, clave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RecargaRequest(new BigDecimal("50.00"), FECHA))))
                .andExpect(status().isCreated());

        // Act & Assert
        mockMvc.perform(conToken(post("/cartera/recargas"), token).header(CABECERA_IDEMPOTENCIA, clave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RecargaRequest(new BigDecimal("60.00"), FECHA))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CLAVE_IDEMPOTENCIA_REUTILIZADA"));
    }

    @Test
    void recarga_sinClaveOConMontoInvalido_responde400() throws Exception {
        // Arrange
        UUID idUsuario = UUID.randomUUID();
        publicarUsuarioCreado(idUsuario, true);
        String token = token(idUsuario, Rol.CLIENTE);

        // Act & Assert
        mockMvc.perform(conToken(post("/cartera/recargas"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RecargaRequest(BigDecimal.TEN, FECHA))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
        mockMvc.perform(conToken(post("/cartera/recargas"), token).header(CABECERA_IDEMPOTENCIA, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RecargaRequest(new BigDecimal("-5"), FECHA))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
    }

    @Test
    void recarga_cuandoLaCuentaEstaInactiva_responde403() throws Exception {
        // Arrange
        UUID idUsuario = UUID.randomUUID();
        publicarUsuarioCreado(idUsuario, false);

        // Act & Assert
        mockMvc.perform(conToken(post("/cartera/recargas"), token(idUsuario, Rol.CLIENTE))
                        .header(CABECERA_IDEMPOTENCIA, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RecargaRequest(BigDecimal.TEN, FECHA))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CUENTA_INACTIVA"));
    }
}
