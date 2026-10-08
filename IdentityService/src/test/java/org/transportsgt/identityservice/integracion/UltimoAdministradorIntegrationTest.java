package org.transportsgt.identityservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Siempre un ADMIN_SISTEMA activo" y "cada sucursal con un ADMIN_SUCURSAL activo":
 * el servicio lo valida y los triggers quedan como respaldo.
 */
class UltimoAdministradorIntegrationTest extends IntegracionBase {

    private UUID crearSucursalConAdmin(String token) throws Exception {
        MvcResult resultado = mockMvc.perform(conToken(post("/sucursales"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "sucursal", Map.of("nombre", unico("Cobán"), "departamento", "Alta Verapaz",
                                        "direccion", "1a. Calle 3-10, Zona 1", "latitud", 15.4703, "longitud", -90.3707),
                                "administrador", Map.of("nombreCompleto", "Rosa Caal",
                                        "correo", unico("admin.coban") + "@transportes.gt")))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(leer(resultado).get("administrador").get("id").asString());
    }

    @Test
    void desactivarUltimoAdminSucursal_responde422PorElServicio() throws Exception {
        // Arrange
        String token = login(ADMIN_SISTEMA, CONTRASENA_ADMIN);
        UUID idAdmin = crearSucursalConAdmin(token);

        // Act + Assert
        mockMvc.perform(conToken(patch("/usuarios/{id}/desactivar", idAdmin), token))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("ULTIMO_ADMIN_SUCURSAL"));
        assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT activo FROM usuario WHERE id = ?", Boolean.class, idAdmin));
    }

    @Test
    void desactivarAdminSucursal_cuandoHayOtro_sePermite() throws Exception {
        // Arrange
        String token = login(ADMIN_SISTEMA, CONTRASENA_ADMIN);
        UUID idPrimero = crearSucursalConAdmin(token);
        UUID idSucursal = jdbc.queryForObject("SELECT id_sucursal FROM usuario WHERE id = ?", UUID.class, idPrimero);
        mockMvc.perform(conToken(post("/usuarios"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nombreCompleto", "Segundo Admin", "rol", "ADMIN_SUCURSAL",
                                "correo", unico("admin2") + "@transportes.gt", "idSucursal", idSucursal))))
                .andExpect(status().isCreated());

        // Act + Assert
        mockMvc.perform(conToken(patch("/usuarios/{id}/desactivar", idPrimero), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
    }

    @Test
    void triggerUltimoAdminSucursal_bloqueaElUpdateDirecto() throws Exception {
        // Arrange
        String token = login(ADMIN_SISTEMA, CONTRASENA_ADMIN);
        UUID idAdmin = crearSucursalConAdmin(token);

        // Act
        DataAccessException ex = assertThrows(DataAccessException.class,
                () -> jdbc.update("UPDATE usuario SET activo = FALSE WHERE id = ?", idAdmin));

        // Assert
        assertTrue(ex.getMostSpecificCause().getMessage()
                .contains("La sucursal debe conservar al menos un administrador de sucursal activo"));
    }

    @Test
    void triggerUltimoAdminSistema_bloqueaDesactivarATodos() {
        // Act: el trigger se dispara en la fila del último ADMIN_SISTEMA activo y revierte la sentencia
        DataAccessException ex = assertThrows(DataAccessException.class,
                () -> jdbc.update("UPDATE usuario SET activo = FALSE WHERE rol = 'ADMIN_SISTEMA'"));

        // Assert
        assertAll(
                () -> assertTrue(ex.getMostSpecificCause().getMessage()
                        .contains("Debe existir al menos un administrador de sistema activo")),
                () -> assertTrue(jdbc.queryForObject(
                        "SELECT count(*) FROM usuario WHERE rol = 'ADMIN_SISTEMA' AND activo", Integer.class) >= 1)
        );
    }

    @Test
    void triggerCreadorAdminSucursal_exigeAdminSistemaActivo() {
        // Act
        DataAccessException ex = assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO usuario (correo, hash_contrasena, nombre_completo, rol, id_sucursal, creado_por)
                VALUES (?, 'x', 'Intruso', 'ADMIN_SUCURSAL', ?, ?)""",
                unico("intruso") + "@transportes.gt", ID_XELA, idUsuarioPorCorreo(CAJERO_XELA)));

        // Assert
        assertTrue(ex.getMostSpecificCause().getMessage()
                .contains("Solo un administrador de sistema activo puede crear administradores de sucursal"));
    }

    @Test
    void triggerSinDelete_impideEliminarUsuarios() {
        DataAccessException ex = assertThrows(DataAccessException.class,
                () -> jdbc.update("DELETE FROM usuario WHERE correo = ?", CLIENTE));

        assertTrue(ex.getMostSpecificCause().getMessage().contains("no se eliminan, solo se desactivan"));
    }

    @Test
    void desactivarUltimoAdminSistema_responde422() throws Exception {
        // Arrange: si otra prueba creó más ADMIN_SISTEMA, se desactivan todos menos el del seed
        String token = login(ADMIN_SISTEMA, CONTRASENA_ADMIN);
        jdbc.update("UPDATE usuario SET activo = FALSE WHERE rol = 'ADMIN_SISTEMA' AND id <> ? AND activo",
                ID_ADMIN_SISTEMA);

        // Act + Assert
        mockMvc.perform(conToken(patch("/usuarios/{id}/desactivar", ID_ADMIN_SISTEMA), token))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("ULTIMO_ADMIN_SISTEMA"))
                .andExpect(jsonPath("$.error").value("Debe existir al menos un administrador de sistema activo"));
    }

    @Test
    void noExisteDelete() throws Exception {
        String token = login(ADMIN_SISTEMA, CONTRASENA_ADMIN);

        mockMvc.perform(conToken(delete("/usuarios/{id}", idUsuarioPorCorreo(CLIENTE)), token))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));
    }
}
