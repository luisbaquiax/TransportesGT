package org.transportsgt.customerwalletservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.transportsgt.customerwalletservice.dto.request.ActualizarPerfilRequest;
import org.transportsgt.eventos.payload.Rol;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PerfilIT extends IntegracionBase {

    @Test
    void perfil_cuandoSeActualizaElNit_seGuardaNormalizadoYSePublica() throws Exception {
        // Arrange
        UUID idUsuario = UUID.randomUUID();
        publicarUsuarioCreado(idUsuario, true);
        String token = token(idUsuario, Rol.CLIENTE);
        String nit = "77" + idUsuario.toString().substring(0, 6).replaceAll("[^0-9]", "1") + "-k";

        // Act
        mockMvc.perform(conToken(put("/clientes/yo"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ActualizarPerfilRequest(nit, null, "55551234", "Zona 1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nit").value(nit.replace("-", "").toUpperCase()))
                .andExpect(jsonPath("$.saldo").value("0.00"));

        // Assert
        mockMvc.perform(conToken(get("/clientes/yo"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefono").value("55551234"));
        assertEquals(nit.replace("-", "").toUpperCase(), jdbc.queryForObject("""
                SELECT datos ->> 'nit' FROM outbox_evento
                 WHERE tipo_evento = 'PerfilClienteActualizado' AND id_agregado = ? ORDER BY version_agregado DESC LIMIT 1
                """, String.class, idUsuario));
    }

    @Test
    void perfil_cuandoElNitEsCF_responde400() throws Exception {
        // Arrange
        UUID idUsuario = UUID.randomUUID();
        publicarUsuarioCreado(idUsuario, true);

        // Act & Assert
        mockMvc.perform(conToken(put("/clientes/yo"), token(idUsuario, Rol.CLIENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ActualizarPerfilRequest("cf", null, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
    }

    @Test
    void perfil_cuandoElNitYaEsDeOtroCliente_responde409() throws Exception {
        // Arrange
        UUID primero = clienteConSaldo("0.00");
        UUID segundo = UUID.randomUUID();
        publicarUsuarioCreado(segundo, true);
        String nitAjeno = jdbc.queryForObject("SELECT nit FROM cliente WHERE id_usuario = ?", String.class, primero);

        // Act & Assert
        mockMvc.perform(conToken(put("/clientes/yo"), token(segundo, Rol.CLIENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ActualizarPerfilRequest(nitAjeno, null, null, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("NIT_DUPLICADO"));
    }

    @Test
    void buscar_soloLoUsanCajeroYAdministradorDeSucursal() throws Exception {
        // Arrange
        UUID idUsuario = clienteConSaldo("0.00");
        String nit = jdbc.queryForObject("SELECT nit FROM cliente WHERE id_usuario = ?", String.class, idUsuario);

        // Act & Assert
        mockMvc.perform(conToken(get("/clientes/buscar").param("nit", nit.toLowerCase()), token(UUID.randomUUID(), Rol.CAJERO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idUsuario").value(idUsuario.toString()));
        mockMvc.perform(conToken(get("/clientes/buscar").param("nit", nit), token(UUID.randomUUID(), Rol.CLIENTE)))
                .andExpect(status().isForbidden());
        mockMvc.perform(conToken(get("/clientes/buscar").param("nit", "000000"), token(UUID.randomUUID(), Rol.ADMIN_SUCURSAL)))
                .andExpect(status().isNotFound());
    }

    @Test
    void perfil_sinToken_responde401() throws Exception {
        mockMvc.perform(get("/clientes/yo"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }
}
