package org.transportsgt.identityservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContrasenaIntegrationTest extends IntegracionBase {

    private static final String MENSAJE_OLVIDE = "Si el correo está registrado, recibirá un enlace para restablecer su contraseña";

    private String registrarCliente(String contrasena) throws Exception {
        String correo = unico("cliente") + "@gmail.com";
        mockMvc.perform(post("/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nombreCompleto", "Cliente Prueba", "correo", correo,
                                "contrasena", contrasena))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("CLIENTE"))
                .andExpect(jsonPath("$.activacionPendiente").value(false));
        return correo;
    }

    private void olvide(String correo) throws Exception {
        mockMvc.perform(post("/auth/olvide-contrasena")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "200.1.2.3")
                        .content(json(Map.of("correo", correo))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_OLVIDE));
    }

    @Test
    void flujoCompletoDeRestablecimiento() throws Exception {
        // Arrange
        String correo = registrarCliente("Inicial123");
        UUID idUsuario = idUsuarioPorCorreo(correo);
        assertEquals(1, contarOutbox(idUsuario, "UsuarioCreado"));

        // Act 1: solicitar el enlace
        olvide(correo.toUpperCase());
        String token = ultimoTokenEnlace(idUsuario, "RESTABLECIMIENTO");
        assertNotNull(token);

        // Act 2: validar el enlace
        mockMvc.perform(get("/auth/enlace-contrasena/validar").param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposito").value("RESTABLECIMIENTO"))
                .andExpect(jsonPath("$.correoEnmascarado").value(correo.charAt(0) + "***"
                        + correo.charAt(correo.indexOf('@') - 1) + "@gmail.com"));

        // Act 3: la misma contraseña no se acepta
        mockMvc.perform(post("/auth/restablecer-contrasena")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token, "nuevaContrasena", "Inicial123"))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CONTRASENA_REPETIDA"));

        // Act 4: restablecer
        mockMvc.perform(post("/auth/restablecer-contrasena")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token, "nuevaContrasena", "Restablecida456"))))
                .andExpect(status().isOk());

        // Assert
        Map<String, Object> registro = jdbc.queryForMap(
                "SELECT usado_en, invalidado_en, ip_solicitud FROM token_contrasena WHERE id_usuario = ?", idUsuario);
        assertAll(
                () -> assertNotNull(registro.get("usado_en")),
                () -> assertNull(registro.get("invalidado_en")),
                () -> assertEquals("200.1.2.3", registro.get("ip_solicitud")),
                () -> assertEquals("RESTABLECIMIENTO", jdbc.queryForObject("""
                        SELECT datos ->> 'motivo' FROM outbox_evento
                         WHERE id_agregado = ? AND tipo_evento = 'ContrasenaCambiada'""", String.class, idUsuario)),
                () -> assertEquals("identidad.usuario.contrasena-cambiada", jdbc.queryForObject("""
                        SELECT llave_ruteo FROM outbox_evento
                         WHERE id_agregado = ? AND tipo_evento = 'ContrasenaCambiada'""", String.class, idUsuario)),
                () -> assertNotNull(jdbc.queryForObject("SELECT contrasena_cambiada_en FROM usuario WHERE id = ?",
                        Object.class, idUsuario))
        );

        // El enlace ya no sirve, la contraseña vieja tampoco y la nueva sí
        mockMvc.perform(get("/auth/enlace-contrasena/validar").param("token", token))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.codigo").value("TOKEN_CONTRASENA_INVALIDO"));
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new Credenciales(correo, "Inicial123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
        assertNotNull(login(correo, "Restablecida456"));
    }

    @Test
    void olvide_nuevoEnlaceInvalidaElAnterior() throws Exception {
        // Arrange
        String correo = registrarCliente("Inicial123");
        UUID idUsuario = idUsuarioPorCorreo(correo);
        olvide(correo);
        String primero = ultimoTokenEnlace(idUsuario, "RESTABLECIMIENTO");

        // Act
        olvide(correo);

        // Assert
        mockMvc.perform(get("/auth/enlace-contrasena/validar").param("token", primero))
                .andExpect(status().isGone());
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM token_contrasena
                 WHERE id_usuario = ? AND usado_en IS NULL AND invalidado_en IS NULL""", Integer.class, idUsuario));
    }

    @Test
    void olvide_limiteDeTresSolicitudesPorHora() throws Exception {
        // Arrange
        String correo = registrarCliente("Inicial123");
        UUID idUsuario = idUsuarioPorCorreo(correo);

        // Act: 5 solicitudes, todas responden 202 con el mismo mensaje
        for (int i = 0; i < 5; i++) {
            olvide(correo);
        }

        // Assert: solo se emitieron 3 enlaces
        assertAll(
                () -> assertEquals(3, jdbc.queryForObject("""
                        SELECT count(*) FROM token_contrasena WHERE id_usuario = ? AND proposito = 'RESTABLECIMIENTO'""",
                        Integer.class, idUsuario)),
                () -> assertEquals(3, contarOutbox(idUsuario, "EnlaceContrasenaEmitido"))
        );
    }

    @Test
    void olvide_correoInexistente_responde202SinEmitir() throws Exception {
        // Arrange
        String correo = unico("nadie") + "@gmail.com";
        Integer antes = jdbc.queryForObject("SELECT count(*) FROM token_contrasena", Integer.class);

        // Act
        olvide(correo);

        // Assert
        assertEquals(antes, jdbc.queryForObject("SELECT count(*) FROM token_contrasena", Integer.class));
    }

    @Test
    void validar_tokenInexistente_responde410() throws Exception {
        mockMvc.perform(get("/auth/enlace-contrasena/validar").param("token", "no-existe"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.codigo").value("TOKEN_CONTRASENA_INVALIDO"));
        mockMvc.perform(get("/auth/enlace-contrasena/validar"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cambiarContrasena_conSesion() throws Exception {
        // Arrange
        String correo = registrarCliente("Inicial123");
        UUID idUsuario = idUsuarioPorCorreo(correo);
        String token = login(correo, "Inicial123");

        // Act + Assert
        mockMvc.perform(conToken(post("/auth/cambiar-contrasena"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("contrasenaActual", "Mala12345", "nuevaContrasena", "Cambiada789"))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CONTRASENA_ACTUAL_INCORRECTA"));
        mockMvc.perform(conToken(post("/auth/cambiar-contrasena"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("contrasenaActual", "Inicial123", "nuevaContrasena", "debil"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
        mockMvc.perform(conToken(post("/auth/cambiar-contrasena"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("contrasenaActual", "Inicial123", "nuevaContrasena", "Cambiada789"))))
                .andExpect(status().isOk());

        assertAll(
                () -> assertEquals("CAMBIO", jdbc.queryForObject("""
                        SELECT datos ->> 'motivo' FROM outbox_evento
                         WHERE id_agregado = ? AND tipo_evento = 'ContrasenaCambiada'""", String.class, idUsuario)),
                () -> assertNotNull(login(correo, "Cambiada789"))
        );
    }

    @Test
    void login_usuarioInactivo_403SoloConLaContrasenaCorrecta() throws Exception {
        // Arrange
        String correo = registrarCliente("Inicial123");
        jdbc.update("UPDATE usuario SET activo = FALSE WHERE correo = ?", correo);

        // Act + Assert
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new Credenciales(correo, "Inicial123"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("USUARIO_INACTIVO"));
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new Credenciales(correo, "Incorrecta1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
    }

    @Test
    void registro_correoDuplicadoYContrasenaDebil() throws Exception {
        mockMvc.perform(post("/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nombreCompleto", "X", "correo", "CLIENTE@transportes.gt",
                                "contrasena", "Cliente1234"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CORREO_DUPLICADO"));
        mockMvc.perform(post("/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nombreCompleto", "X", "correo", unico("x") + "@gmail.com",
                                "contrasena", "sinmayuscula1"))))
                .andExpect(status().isBadRequest());
    }
}
