package org.transportsgt.identityservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SucursalIntegrationTest extends IntegracionBase {

    private Map<String, Object> cuerpoSucursal(String nombre, String correoAdmin) {
        return Map.of(
                "sucursal", Map.of("nombre", nombre, "departamento", "Retalhuleu",
                        "direccion", "5a. Avenida 6-20, Zona 1", "telefono", "77711234",
                        "latitud", 14.536400, "longitud", -91.677800),
                "administrador", Map.of("nombreCompleto", "Pedro Juárez", "correo", correoAdmin));
    }

    @Test
    void crearSucursal_creaSucursalYAdministradorYGuardaEventosEnOutbox() throws Exception {
        // Arrange
        String token = login(ADMIN_SISTEMA, CONTRASENA_ADMIN);
        String nombre = unico("Retalhuleu");
        String correoAdmin = unico("admin.reu") + "@transportes.gt";

        // Act
        MvcResult resultado = mockMvc.perform(conToken(post("/sucursales"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cuerpoSucursal(nombre, correoAdmin))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sucursal.nombre").value(nombre))
                .andExpect(jsonPath("$.sucursal.activa").value(true))
                .andExpect(jsonPath("$.administrador.rol").value("ADMIN_SUCURSAL"))
                .andExpect(jsonPath("$.administrador.activacionPendiente").value(true))
                .andReturn();

        // Assert
        JsonNode cuerpo = leer(resultado);
        UUID idSucursal = UUID.fromString(cuerpo.get("sucursal").get("id").asString());
        UUID idAdmin = UUID.fromString(cuerpo.get("administrador").get("id").asString());
        Map<String, Object> admin = jdbc.queryForMap("SELECT id_sucursal, creado_por, rol FROM usuario WHERE id = ?", idAdmin);
        Map<String, Object> sucursalCreada = jdbc.queryForMap("""
                SELECT llave_ruteo, tipo_agregado, version_agregado, datos ->> 'nombre' AS nombre
                  FROM outbox_evento WHERE id_agregado = ? AND tipo_evento = 'SucursalCreada'""", idSucursal);
        assertAll(
                () -> assertEquals(idSucursal, admin.get("id_sucursal")),
                () -> assertEquals(ID_ADMIN_SISTEMA, admin.get("creado_por")),
                () -> assertEquals("identidad.sucursal.creada", sucursalCreada.get("llave_ruteo")),
                () -> assertEquals("sucursal", sucursalCreada.get("tipo_agregado")),
                () -> assertEquals(0L, sucursalCreada.get("version_agregado")),
                () -> assertEquals(nombre, sucursalCreada.get("nombre")),
                () -> assertEquals("identidad.usuario.creado", jdbc.queryForObject(
                        "SELECT llave_ruteo FROM outbox_evento WHERE id_agregado = ? AND tipo_evento = 'UsuarioCreado'",
                        String.class, idAdmin)),
                () -> assertEquals("identidad.usuario.enlace-contrasena-emitido", jdbc.queryForObject(
                        "SELECT llave_ruteo FROM outbox_evento WHERE id_agregado = ? AND tipo_evento = 'EnlaceContrasenaEmitido'",
                        String.class, idAdmin)),
                () -> assertEquals(1, jdbc.queryForObject("""
                        SELECT count(*) FROM token_contrasena
                         WHERE id_usuario = ? AND proposito = 'ACTIVACION' AND usado_en IS NULL""", Integer.class, idAdmin))
        );
    }

    @Test
    void crearSucursal_elAdministradorActivaSuCuentaEIniciaSesion() throws Exception {
        // Arrange
        String token = login(ADMIN_SISTEMA, CONTRASENA_ADMIN);
        String correoAdmin = unico("admin.activa") + "@transportes.gt";
        mockMvc.perform(conToken(post("/sucursales"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cuerpoSucursal(unico("Mazatenango"), correoAdmin))))
                .andExpect(status().isCreated());
        UUID idAdmin = idUsuarioPorCorreo(correoAdmin);
        String enlace = ultimoTokenEnlace(idAdmin, "ACTIVACION");

        // Act
        mockMvc.perform(get("/auth/enlace-contrasena/validar").param("token", enlace))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposito").value("ACTIVACION"));
        mockMvc.perform(post("/auth/restablecer-contrasena")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", enlace, "nuevaContrasena", "AdminReu2026"))))
                .andExpect(status().isOk());

        // Assert
        assertAll(
                () -> assertNotNull(login(correoAdmin, "AdminReu2026")),
                () -> assertEquals("ACTIVACION", jdbc.queryForObject("""
                        SELECT datos ->> 'motivo' FROM outbox_evento
                         WHERE id_agregado = ? AND tipo_evento = 'ContrasenaCambiada'""", String.class, idAdmin))
        );
    }

    @Test
    void crearSucursal_conNombreDuplicado_responde409YNoCreaNada() throws Exception {
        // Arrange
        String token = login(ADMIN_SISTEMA, CONTRASENA_ADMIN);
        String correoAdmin = unico("admin.dup") + "@transportes.gt";

        // Act
        mockMvc.perform(conToken(post("/sucursales"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cuerpoSucursal("Quetzaltenango", correoAdmin))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("NOMBRE_SUCURSAL_DUPLICADO"))
                .andExpect(jsonPath("$.ruta").value("/sucursales"));

        // Assert
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM usuario WHERE correo = ?", Integer.class, correoAdmin));
    }

    @Test
    void crearSucursal_conCorreoDuplicado_revierteTambienLaSucursal() throws Exception {
        // Arrange
        String token = login(ADMIN_SISTEMA, CONTRASENA_ADMIN);
        String nombre = unico("Coatepeque");

        // Act
        mockMvc.perform(conToken(post("/sucursales"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cuerpoSucursal(nombre, CAJERO_XELA))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CORREO_DUPLICADO"));

        // Assert: misma transacción
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM sucursal WHERE nombre = ?", Integer.class, nombre));
    }

    @Test
    void crearSucursal_conDatosInvalidos_responde400() throws Exception {
        String token = login(ADMIN_SISTEMA, CONTRASENA_ADMIN);

        mockMvc.perform(conToken(post("/sucursales"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sucursal\":{\"nombre\":\"\",\"latitud\":200},\"administrador\":{\"correo\":\"x\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
    }

    @Test
    void consultarActualizarYCambiarEstado() throws Exception {
        // Arrange
        String tokenAdmin = login(ADMIN_SISTEMA, CONTRASENA_ADMIN);
        String tokenCajero = login(CAJERO_XELA, CONTRASENA_CAJERO);
        MvcResult creada = mockMvc.perform(conToken(post("/sucursales"), tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cuerpoSucursal(unico("Chimaltenango"), unico("admin.chimal") + "@transportes.gt"))))
                .andExpect(status().isCreated()).andReturn();
        UUID id = UUID.fromString(leer(creada).get("sucursal").get("id").asString());
        String nuevoNombre = unico("Chimaltenango Centro");

        // Act + Assert
        mockMvc.perform(conToken(get("/sucursales"), tokenCajero).param("activa", "true").param("nombre", "quetzal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.contenido[0].nombre").value("Quetzaltenango"));
        mockMvc.perform(conToken(get("/sucursales/{id}", id), tokenCajero))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
        mockMvc.perform(conToken(put("/sucursales/{id}", id), tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sucursal", Map.of("nombre", nuevoNombre, "departamento", "Chimaltenango",
                                "direccion", "1a. Calle 2-30", "latitud", 14.661, "longitud", -90.819), "version", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value(nuevoNombre))
                .andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(conToken(put("/sucursales/{id}", id), tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sucursal", Map.of("nombre", nuevoNombre, "departamento", "Chimaltenango",
                                "direccion", "1a. Calle 2-30", "latitud", 14.661, "longitud", -90.819), "version", 0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_CONCURRENCIA"));
        mockMvc.perform(conToken(patch("/sucursales/{id}/desactivar", id), tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(false));
        mockMvc.perform(conToken(patch("/sucursales/{id}/activar", id), tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(true));
        mockMvc.perform(conToken(get("/sucursales/{id}/administradores", id), tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rol").value("ADMIN_SUCURSAL"));
        mockMvc.perform(conToken(get("/sucursales/{id}", UUID.randomUUID()), tokenAdmin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SUCURSAL_NO_ENCONTRADA"));

        // 1 actualización + desactivar + activar = 3 SucursalActualizada con versiones 1, 2 y 3
        assertAll(
                () -> assertEquals(3, contarOutbox(id, "SucursalActualizada")),
                () -> assertEquals(3L, jdbc.queryForObject("""
                        SELECT max(version_agregado) FROM outbox_evento
                         WHERE id_agregado = ? AND tipo_evento = 'SucursalActualizada'""", Long.class, id))
        );
    }
}
