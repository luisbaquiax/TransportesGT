package org.transportsgt.customerwalletservice.integracion;

import org.junit.jupiter.api.Test;
import org.transportsgt.eventos.payload.identidad.Rol;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RepublicacionIT extends IntegracionBase {

    /**
     * Cliente de los datos iniciales.
     */
    private static final UUID CLIENTE_SEMILLA = UUID.fromString("00000000-0000-4000-8000-000000000031");
    private static final UUID ADMIN_SISTEMA = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Test
    void republicar_comoAdminSistema_guardaUnPerfilPorClienteConSuVersion() throws Exception {
        // Arrange
        int antes = perfilesPublicados(CLIENTE_SEMILLA);
        int clientes = jdbc.queryForObject("SELECT count(*) FROM cliente", Integer.class);

        // Act
        mockMvc.perform(conToken(post("/admin/republicar-eventos"), token(ADMIN_SISTEMA, Rol.ADMIN_SISTEMA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientes").value(clientes));

        // Assert
        assertEquals(antes + 1, perfilesPublicados(CLIENTE_SEMILLA));
        assertEquals(jdbc.queryForObject("SELECT version FROM cliente WHERE id_usuario = ?", Long.class,
                CLIENTE_SEMILLA), jdbc.queryForObject("""
                SELECT version_agregado FROM outbox_evento
                 WHERE tipo_evento = 'PerfilClienteActualizado' AND id_agregado = ? ORDER BY creado_en DESC LIMIT 1
                """, Long.class, CLIENTE_SEMILLA));
    }

    @Test
    void republicar_conOtroRol_responde403() throws Exception {
        mockMvc.perform(conToken(post("/admin/republicar-eventos"), token(CLIENTE_SEMILLA, Rol.CLIENTE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void republicar_sinToken_responde401() throws Exception {
        mockMvc.perform(post("/admin/republicar-eventos"))
                .andExpect(status().isUnauthorized());
    }

    private int perfilesPublicados(UUID idUsuario) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM outbox_evento WHERE tipo_evento = 'PerfilClienteActualizado' AND id_agregado = ?
                """, Integer.class, idUsuario);
    }
}
