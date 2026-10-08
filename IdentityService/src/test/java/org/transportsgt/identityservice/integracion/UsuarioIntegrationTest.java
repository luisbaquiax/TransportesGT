package org.transportsgt.identityservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.transportsgt.eventos.TopologiaEventos;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UsuarioIntegrationTest extends IntegracionBase {

    @Autowired
    private AmqpAdmin amqpAdmin;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    private UUID crearCajero(String tokenAdminXela, String correo) throws Exception {
        MvcResult resultado = mockMvc.perform(conToken(post("/usuarios"), tokenAdminXela)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nombreCompleto", "Cajero Nuevo", "correo", correo, "rol", "CAJERO"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("CAJERO"))
                .andExpect(jsonPath("$.idSucursal").value(ID_XELA.toString()))
                .andExpect(jsonPath("$.activacionPendiente").value(true))
                .andReturn();
        return UUID.fromString(leer(resultado).get("id").asString());
    }

    @Test
    void adminSucursal_creaCajeroEnSuSucursal_yElEventoLlegaARabbitMQ() throws Exception {
        // Arrange: cola temporal enlazada al exchange de eventos
        TopicExchange exchange = new TopicExchange(TopologiaEventos.EXCHANGE, true, false);
        amqpAdmin.declareExchange(exchange);
        // Durable y no exclusiva (RabbitMQ 4 no admite colas transitorias no exclusivas); se borra al final
        Queue cola = new Queue("prueba.identidad." + UUID.randomUUID(), true, false, false);
        amqpAdmin.declareQueue(cola);
        amqpAdmin.declareBinding(BindingBuilder.bind(cola).to(exchange).with("identidad.usuario.creado"));
        String token = login(ADMIN_XELA, CONTRASENA_ADMIN);
        String correo = unico("cajero") + "@transportes.gt";

        // Act
        UUID idCajero = crearCajero(token, correo);

        // Assert: el poller del outbox lo publicó con el sobre del catálogo
        JsonNode sobre;
        try {
            sobre = recibirEventoDe(cola.getName(), idCajero);
        } finally {
            amqpAdmin.deleteQueue(cola.getName());
        }
        assertAll(
                () -> assertNotNull(sobre, "El evento no llegó a RabbitMQ"),
                () -> assertEquals("UsuarioCreado", sobre.get("tipoEvento").asString()),
                () -> assertEquals("servicio-identidad", sobre.get("servicioProductor").asString()),
                () -> assertEquals(0L, sobre.get("versionAgregado").asLong()),
                () -> assertEquals(1, sobre.get("versionEvento").asInt()),
                () -> assertEquals(correo, sobre.get("datos").get("correo").asString()),
                () -> assertEquals(ID_XELA.toString(), sobre.get("datos").get("idSucursal").asString()),
                () -> assertTrue(sobre.get("idCorrelacion").isString()),
                () -> assertEquals(idUsuarioPorCorreo(ADMIN_XELA), jdbc.queryForObject(
                        "SELECT creado_por FROM usuario WHERE id = ?", UUID.class, idCajero))
        );
    }

    private JsonNode recibirEventoDe(String cola, UUID idAgregado) {
        long limite = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < limite) {
            Message mensaje = rabbitTemplate.receive(cola, 1_000);
            if (mensaje != null) {
                JsonNode sobre = jsonMapper.readTree(mensaje.getBody());
                if (idAgregado.toString().equals(sobre.get("idAgregado").asString())) {
                    return sobre;
                }
            }
        }
        return null;
    }

    @Test
    void adminSucursal_noPuedeCrearEnOtraSucursalNiAdministradores() throws Exception {
        String token = login(ADMIN_XELA, CONTRASENA_ADMIN);

        mockMvc.perform(conToken(post("/usuarios"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nombreCompleto", "X", "correo", unico("x") + "@transportes.gt",
                                "rol", "CHOFER", "idSucursal", ID_CAPITAL))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("OPERACION_NO_AUTORIZADA"));
        mockMvc.perform(conToken(post("/usuarios"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nombreCompleto", "X", "correo", unico("x") + "@transportes.gt",
                                "rol", "ADMIN_SUCURSAL"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminSistema_creaAdminSistema_yValidaSucursalSegunRol() throws Exception {
        String token = login(ADMIN_SISTEMA, CONTRASENA_ADMIN);

        mockMvc.perform(conToken(post("/usuarios"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nombreCompleto", "Otro Admin", "correo", unico("admin") + "@transportes.gt",
                                "rol", "ADMIN_SISTEMA"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idSucursal").doesNotExist());
        mockMvc.perform(conToken(post("/usuarios"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nombreCompleto", "X", "correo", unico("x") + "@transportes.gt",
                                "rol", "ADMIN_SUCURSAL"))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("SUCURSAL_NO_PERMITIDA"));
        mockMvc.perform(conToken(post("/usuarios"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nombreCompleto", "X", "correo", CAJERO_XELA.toUpperCase(),
                                "rol", "ADMIN_SISTEMA"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CORREO_DUPLICADO"));
    }

    @Test
    void listarConsultarEditarYCambiarEstado() throws Exception {
        // Arrange
        String tokenXela = login(ADMIN_XELA, CONTRASENA_ADMIN);
        UUID idCajero = crearCajero(tokenXela, unico("cajero") + "@transportes.gt");
        String correoNuevo = unico("editado") + "@transportes.gt";

        // Act + Assert
        mockMvc.perform(conToken(get("/usuarios"), tokenXela).param("rol", "CAJERO").param("activo", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.contenido[*].idSucursal", everyItem(is(ID_XELA.toString()))));
        mockMvc.perform(conToken(get("/usuarios"), tokenXela).param("idSucursal", ID_CAPITAL.toString()))
                .andExpect(status().isForbidden());
        mockMvc.perform(conToken(get("/usuarios/{id}", idCajero), tokenXela))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idCajero.toString()));
        mockMvc.perform(conToken(put("/usuarios/{id}", idCajero), tokenXela)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nombreCompleto", "Cajero Editado", "correo", correoNuevo.toUpperCase(),
                                "version", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value(correoNuevo))
                .andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(conToken(post("/usuarios/{id}/reenviar-activacion", idCajero), tokenXela))
                .andExpect(status().isAccepted());
        mockMvc.perform(conToken(patch("/usuarios/{id}/desactivar", idCajero), tokenXela))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
        mockMvc.perform(conToken(post("/usuarios/{id}/reenviar-activacion", idCajero), tokenXela))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("ACTIVACION_NO_PENDIENTE"));
        mockMvc.perform(conToken(patch("/usuarios/{id}/activar", idCajero), tokenXela))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true));

        // Admin de otra sucursal no lo ve; un id inexistente da 404
        String tokenCapital = login(ADMIN_CAPITAL, CONTRASENA_ADMIN);
        mockMvc.perform(conToken(get("/usuarios/{id}", idCajero), tokenCapital))
                .andExpect(status().isForbidden());
        mockMvc.perform(conToken(get("/usuarios/{id}", UUID.randomUUID()), tokenXela))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("USUARIO_NO_ENCONTRADO"));

        // UsuarioCreado (v0) + editar (v1) + desactivar (v2) + activar (v3)
        assertAll(
                () -> assertEquals(1, contarOutbox(idCajero, "UsuarioCreado")),
                () -> assertEquals(3, contarOutbox(idCajero, "UsuarioActualizado")),
                () -> assertEquals(2, contarOutbox(idCajero, "EnlaceContrasenaEmitido")),
                () -> assertEquals("false", jdbc.queryForObject("""
                        SELECT datos ->> 'activo' FROM outbox_evento
                         WHERE id_agregado = ? AND tipo_evento = 'UsuarioActualizado' AND version_agregado = 2""",
                        String.class, idCajero))
        );
    }

    @Test
    void actualizarMiPerfil() throws Exception {
        // Arrange
        String correo = unico("perfil") + "@gmail.com";
        mockMvc.perform(post("/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nombreCompleto", "Nombre Viejo", "correo", correo, "contrasena", "Perfil1234"))))
                .andExpect(status().isCreated());
        String token = login(correo, "Perfil1234");

        // Act + Assert
        mockMvc.perform(conToken(put("/usuarios/yo"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nombreCompleto", "Nombre Nuevo"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreCompleto").value("Nombre Nuevo"));
        assertEquals(1, contarOutbox(idUsuarioPorCorreo(correo), "UsuarioActualizado"));
    }

    @Test
    void republicarEventos_guardaSucursalCreadaYUsuarioCreadoDeTodo() throws Exception {
        // Arrange
        String token = login(ADMIN_SISTEMA, CONTRASENA_ADMIN);
        int sucursales = jdbc.queryForObject("SELECT count(*) FROM sucursal", Integer.class);
        int usuarios = jdbc.queryForObject("SELECT count(*) FROM usuario", Integer.class);
        UUID idCliente = idUsuarioPorCorreo(CLIENTE);
        int antes = contarOutbox(idCliente, "UsuarioCreado");

        // Act + Assert
        mockMvc.perform(conToken(post("/admin/republicar-eventos"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sucursales").value(sucursales))
                .andExpect(jsonPath("$.usuarios").value(usuarios));
        assertAll(
                () -> assertEquals(antes + 1, contarOutbox(idCliente, "UsuarioCreado")),
                () -> assertTrue(contarOutbox(ID_XELA, "SucursalCreada") >= 1)
        );
    }
}
