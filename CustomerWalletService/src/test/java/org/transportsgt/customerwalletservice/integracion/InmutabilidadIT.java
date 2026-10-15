package org.transportsgt.customerwalletservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.transportsgt.customerwalletservice.dto.request.RecargaRequest;
import org.transportsgt.eventos.payload.Rol;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InmutabilidadIT extends IntegracionBase {

    @Test
    void movimientoCartera_noSePuedeModificarNiEliminar() throws Exception {
        // Arrange
        UUID idUsuario = UUID.randomUUID();
        publicarUsuarioCreado(idUsuario, true);
        mockMvc.perform(conToken(post("/cartera/recargas"), token(idUsuario, Rol.CLIENTE))
                        .header(CABECERA_IDEMPOTENCIA, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RecargaRequest(BigDecimal.TEN, LocalDate.parse("2026-10-20")))))
                .andExpect(status().isCreated());
        UUID idMovimiento = jdbc.queryForObject("""
                SELECT m.id FROM movimiento_cartera m JOIN cartera ca ON ca.id = m.id_cartera
                  JOIN cliente c ON c.id = ca.id_cliente WHERE c.id_usuario = ?
                """, UUID.class, idUsuario);

        // Act
        DataAccessException alModificar = assertThrows(DataAccessException.class,
                () -> jdbc.update("UPDATE movimiento_cartera SET monto = 1 WHERE id = ?", idMovimiento));
        DataAccessException alEliminar = assertThrows(DataAccessException.class,
                () -> jdbc.update("DELETE FROM movimiento_cartera WHERE id = ?", idMovimiento));

        // Assert
        assertAll(
                () -> assertTrue(alModificar.getMostSpecificCause().getMessage().contains("son inmutables")),
                () -> assertTrue(alEliminar.getMostSpecificCause().getMessage().contains("son inmutables"))
        );
    }
}
