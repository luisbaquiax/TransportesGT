package org.transportsgt.reportingservice.integracion;

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
import org.transportsgt.eventos.ConvertidorEventos;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.TopologiaEventos;
import org.transportsgt.eventos.payload.Rol;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.spec.SecretKeySpec;
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
        "spring.jpa.show-sql=false",
        "logging.level.org.transportsgt=INFO",
        // Reintentos cortos: los transitorios se resuelven o llegan a la DLQ en segundos
        "spring.rabbitmq.listener.simple.retry.initial-interval=200ms",
        "spring.rabbitmq.listener.simple.retry.max-interval=1s"
})
@AutoConfigureMockMvc
@Import(ContenedoresConfig.class)
public abstract class IntegracionBase {

    static final String SECRETO_JWT = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final Duration ESPERA_MAXIMA = Duration.ofSeconds(20);

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

    /** Token como el que emite Identidad (mismos claims y emisor). */
    protected String token(Rol rol, UUID idSucursal) {
        Instant ahora = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("servicio-identidad")
                .subject(UUID.randomUUID().toString())
                .claim("correo", "usuario@transportes.gt")
                .claim("nombre", "Usuario de prueba")
                .claim("rol", rol.name())
                .issuedAt(ahora)
                .expiresAt(ahora.plusSeconds(1800));
        if (idSucursal != null) {
            claims.claim("idSucursal", idSucursal.toString());
        }
        return codificador.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                claims.build())).getTokenValue();
    }

    protected String tokenSistema() {
        return token(Rol.ADMIN_SISTEMA, null);
    }

    protected String tokenSucursal(UUID idSucursal) {
        return token(Rol.ADMIN_SUCURSAL, idSucursal);
    }

    protected static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, String token) {
        return peticion.header("Authorization", "Bearer " + token);
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

    /** Espera a que el consumidor termine (no hay Awaitility en el proyecto). */
    protected void esperar(String descripcion, BooleanSupplier condicion) {
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
}
