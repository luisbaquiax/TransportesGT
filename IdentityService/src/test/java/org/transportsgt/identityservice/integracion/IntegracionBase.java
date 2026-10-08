package org.transportsgt.identityservice.integracion;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base de las pruebas de integración: contexto completo, contenedores y utilidades HTTP.
 * Las rutas van sin el context-path (MockMvc no lo aplica).
 */
@SpringBootTest(properties = {
        // Independiente del .env local
        "SPRING_DATASOURCE_URL=jdbc:postgresql://ignorado:5432/ignorado",
        "SPRING_DATASOURCE_USERNAME=ignorado",
        "SPRING_DATASOURCE_PASSWORD=ignorado",
        "RABBITMQ_URL=amqp://ignorado:5672",
        "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "jwt.expiration=30m",
        "spring.jpa.show-sql=false",
        "logging.level.org.transportsgt=INFO",
        "eventos.outbox.intervalo-ms=200"
})
@AutoConfigureMockMvc
@Import(ContenedoresConfig.class)
public abstract class IntegracionBase {

    /** Datos de V2__datos_iniciales.sql */
    protected static final String ADMIN_SISTEMA = "admin@transportes.gt";
    protected static final UUID ID_ADMIN_SISTEMA = UUID.fromString("00000000-0000-4000-8000-000000000001");
    protected static final String ADMIN_XELA = "admin.xela@transportes.gt";
    protected static final String ADMIN_CAPITAL = "admin.capital@transportes.gt";
    protected static final UUID ID_XELA = UUID.fromString("10000000-0000-4000-8000-000000000001");
    protected static final UUID ID_CAPITAL = UUID.fromString("10000000-0000-4000-8000-000000000002");
    protected static final String CAJERO_XELA = "cajero.xela@transportes.gt";
    protected static final String CLIENTE = "cliente@transportes.gt";
    protected static final String CONTRASENA_ADMIN = "Admin1234";
    protected static final String CONTRASENA_CAJERO = "Cajero1234";
    protected static final String CONTRASENA_CLIENTE = "Cliente1234";

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected JsonMapper jsonMapper;
    @Autowired
    protected JdbcTemplate jdbc;

    protected String login(String correo, String contrasena) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new Credenciales(correo, contrasena))))
                .andExpect(status().isOk())
                .andReturn();
        return leer(resultado).get("token").asString();
    }

    protected static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, String token) {
        return peticion.header("Authorization", "Bearer " + token);
    }

    protected String json(Object cuerpo) {
        return jsonMapper.writeValueAsString(cuerpo);
    }

    protected JsonNode leer(MvcResult resultado) throws Exception {
        return jsonMapper.readTree(resultado.getResponse().getContentAsString());
    }

    protected static String unico(String prefijo) {
        return prefijo + "." + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Token en claro del último enlace emitido al usuario (solo viaja en el outbox). */
    protected String ultimoTokenEnlace(UUID idUsuario, String proposito) {
        return jdbc.queryForObject("""
                SELECT datos ->> 'token' FROM outbox_evento
                 WHERE tipo_evento = 'EnlaceContrasenaEmitido' AND id_agregado = ?
                   AND datos ->> 'proposito' = ?
                 ORDER BY creado_en DESC, datos ->> 'expiraEn' DESC LIMIT 1
                """, String.class, idUsuario, proposito);
    }

    protected UUID idUsuarioPorCorreo(String correo) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE correo = ?", UUID.class, correo);
    }

    protected int contarOutbox(UUID idAgregado, String tipoEvento) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE id_agregado = ? AND tipo_evento = ?",
                Integer.class, idAgregado, tipoEvento);
        return n == null ? 0 : n;
    }

    protected record Credenciales(String correo, String contrasena) {
    }
}
