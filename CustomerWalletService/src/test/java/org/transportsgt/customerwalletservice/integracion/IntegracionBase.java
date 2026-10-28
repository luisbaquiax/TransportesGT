package org.transportsgt.customerwalletservice.integracion;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.transportsgt.eventos.contrato.ConvertidorEventos;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.mensajeria.rabbit.TopologiaEventos;
import org.transportsgt.eventos.payload.identidad.DatosUsuario;
import org.transportsgt.eventos.payload.identidad.Rol;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Base de las pruebas de integración: contexto completo, contenedores, JWT firmados como los de Identidad,
 * publicación de eventos en RabbitMQ y espera hasta que el consumidor los procese.
 * Las rutas van sin el context-path (MockMvc no lo aplica).
 */
@SpringBootTest(properties = {
        // Independiente del .env local
        "SPRING_DATASOURCE_URL=jdbc:postgresql://ignorado:5432/ignorado",
        "SPRING_DATASOURCE_USERNAME=ignorado",
        "SPRING_DATASOURCE_PASSWORD=ignorado",
        "RABBITMQ_URL=amqp://ignorado:5672",
        "jwt.secret=" + IntegracionBase.SECRETO_JWT,
        "CLAVE_INTERNA=" + IntegracionBase.CLAVE_INTERNA,
        "spring.jpa.show-sql=false",
        "logging.level.org.transportsgt=INFO",
        "eventos.outbox.intervalo-ms=200",
        // Reintentos cortos para que el mensaje inválido llegue rápido a la DLQ
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms"
})
@AutoConfigureMockMvc
@Import(ContenedoresConfig.class)
public abstract class IntegracionBase {

    static final String SECRETO_JWT = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    static final String CLAVE_INTERNA = "clave-interna-de-prueba";
    static final String CABECERA_IDEMPOTENCIA = "Clave-Idempotencia";
    private static final Duration ESPERA_MAXIMA = Duration.ofSeconds(15);

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected JsonMapper jsonMapper;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected RabbitTemplate rabbit;
    @Autowired
    protected ConvertidorEventos convertidor;

    private final NimbusJwtEncoder codificador = new NimbusJwtEncoder(new ImmutableSecret<>(
            new SecretKeySpec(Base64.getDecoder().decode(SECRETO_JWT), "HmacSHA256")));

    /**
     * Token como el que emite Identidad (mismos claims y emisor).
     */
    protected String token(UUID idUsuario, Rol rol) {
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("servicio-identidad")
                .subject(idUsuario.toString())
                .claim("correo", "usuario@transportes.gt")
                .claim("nombre", "Usuario de prueba")
                .claim("rol", rol.name())
                .issuedAt(ahora)
                .expiresAt(ahora.plusSeconds(1800))
                .build();
        return codificador.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
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

    // ---------- Eventos ----------

    protected <T> SobreEvento<T> sobre(TipoEvento tipo, UUID idAgregado, Long version, T datos) {
        return new SobreEvento<>(UUID.randomUUID(), tipo.nombre(), 1, Instant.now(), tipo.productor().nombreServicio(),
                idAgregado, version, UUID.randomUUID(), datos);
    }

    protected void publicar(TipoEvento tipo, SobreEvento<?> sobre) {
        publicarCrudo(tipo.llaveRuteo(), convertidor.serializar(sobre));
    }

    protected void publicarCrudo(String llaveRuteo, byte[] cuerpo) {
        Message mensaje = MessageBuilder.withBody(cuerpo).setContentType("application/json").build();
        rabbit.send(TopologiaEventos.EXCHANGE, llaveRuteo, mensaje);
    }

    /**
     * Espera a que el consumidor termine (no hay Awaitility en el proyecto).
     */
    protected static void esperar(String descripcion, BooleanSupplier condicion) {
        long limite = System.nanoTime() + ESPERA_MAXIMA.toNanos();
        while (System.nanoTime() < limite) {
            if (condicion.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrumpido esperando: " + descripcion);
            }
        }
        fail("Tiempo agotado esperando: " + descripcion);
    }

    protected boolean eventoProcesado(UUID idEvento) {
        return jdbc.queryForObject("SELECT count(*) FROM evento_procesado WHERE id_evento = ?",
                Integer.class, idEvento) > 0;
    }

    // ---------- Datos de escenario ----------

    protected void publicarUsuarioCreado(UUID idUsuario, boolean activo) {
        SobreEvento<DatosUsuario> evento = sobre(TipoEvento.USUARIO_CREADO, idUsuario, 0L,
                new DatosUsuario(idUsuario, "u." + idUsuario + "@transportes.gt", "Cliente " + idUsuario.toString()
                        .substring(0, 8), Rol.CLIENTE, null, activo));
        publicar(TipoEvento.USUARIO_CREADO, evento);
        esperar("cliente " + idUsuario, () -> clientes(idUsuario) == 1);
    }

    /**
     * Cliente creado por UsuarioCreado, con NIT y saldo listos para pagar.
     */
    protected UUID clienteConSaldo(String saldo) {
        UUID idUsuario = UUID.randomUUID();
        publicarUsuarioCreado(idUsuario, true);
        String nit = idUsuario.toString().replace("-", "").substring(0, 12).toUpperCase();
        jdbc.update("UPDATE cliente SET nit = ? WHERE id_usuario = ?", nit, idUsuario);
        jdbc.update("UPDATE cartera SET saldo = ? WHERE id_cliente = (SELECT id FROM cliente WHERE id_usuario = ?)",
                new BigDecimal(saldo), idUsuario);
        return idUsuario;
    }

    protected int clientes(UUID idUsuario) {
        return jdbc.queryForObject("SELECT count(*) FROM cliente WHERE id_usuario = ?", Integer.class, idUsuario);
    }

    protected BigDecimal saldo(UUID idUsuario) {
        return jdbc.queryForObject("""
                SELECT ca.saldo FROM cartera ca JOIN cliente c ON c.id = ca.id_cliente WHERE c.id_usuario = ?
                """, BigDecimal.class, idUsuario);
    }

    protected int movimientos(UUID idUsuario, String tipo) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM movimiento_cartera m
                  JOIN cartera ca ON ca.id = m.id_cartera JOIN cliente c ON c.id = ca.id_cliente
                 WHERE c.id_usuario = ? AND m.tipo = ?
                """, Integer.class, idUsuario, tipo);
    }
}
