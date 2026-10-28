package org.transportsgt.ticketservice.integracion;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.transportsgt.eventos.contrato.ConvertidorEventos;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.mensajeria.rabbit.TopologiaEventos;
import org.transportsgt.eventos.payload.flota.BusAsignado;
import org.transportsgt.eventos.payload.flota.ChoferAsignado;
import org.transportsgt.eventos.payload.cartera.DatosPerfilClienteActualizado;
import org.transportsgt.eventos.payload.viajes.DatosViaje;
import org.transportsgt.eventos.payload.viajes.EstadoViaje;
import org.transportsgt.eventos.payload.identidad.Rol;
import org.transportsgt.eventos.payload.viajes.TipoViaje;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Base de las pruebas de integración: contexto completo, contenedores, Cartera simulada con WireMock, JWT firmados
 * como los de Identidad, publicación de eventos en RabbitMQ y espera hasta que el consumidor los procese.
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
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms",
        // El job se invoca a mano en las pruebas
        "boletos.recuperacion.intervalo-ms=3600000",
        "resilience4j.retry.instances.cartera.wait-duration=50ms"
})
@AutoConfigureMockMvc
@Import(ContenedoresConfig.class)
public abstract class IntegracionBase {

    static final String SECRETO_JWT = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    static final String CLAVE_INTERNA = "clave-interna-de-prueba";
    static final String PAGOS = "/v1/customers/cartera/pagos";
    static final UUID SUCURSAL_XELA = UUID.fromString("10000000-0000-4000-8000-000000000001");
    static final UUID SUCURSAL_CAPITAL = UUID.fromString("10000000-0000-4000-8000-000000000002");
    private static final Duration ESPERA_MAXIMA = Duration.ofSeconds(15);

    /** Cartera simulada; vive lo que dura la JVM de las pruebas (el contexto de Spring también se reutiliza). */
    static final WireMockServer CARTERA = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        CARTERA.start();
    }

    @DynamicPropertySource
    static void carteraSimulada(DynamicPropertyRegistry registro) {
        registro.add("cartera.url", () -> CARTERA.baseUrl() + "/v1/customers");
    }

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
    @Autowired
    private CircuitBreakerRegistry circuitos;

    private final NimbusJwtEncoder codificador = new NimbusJwtEncoder(new ImmutableSecret<>(
            new SecretKeySpec(Base64.getDecoder().decode(SECRETO_JWT), "HmacSHA256")));

    @BeforeEach
    void reiniciarCartera() {
        CARTERA.resetAll();
        // Las fallas simuladas de una prueba no deben abrir el circuito en la siguiente
        circuitos.circuitBreaker("cartera").reset();
    }

    // ---------- HTTP ----------

    /** Token como el que emite Identidad (mismos claims y emisor). */
    protected String token(UUID idUsuario, Rol rol, UUID idSucursal) {
        Instant ahora = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("servicio-identidad")
                .subject(idUsuario.toString())
                .claim("correo", "usuario@transportes.gt")
                .claim("nombre", "Usuario de prueba")
                .claim("rol", rol.name())
                .issuedAt(ahora)
                .expiresAt(ahora.plusSeconds(1800));
        if (idSucursal != null) {
            claims.claim("idSucursal", idSucursal.toString());
        }
        return codificador.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }

    protected String token(UUID idUsuario) {
        return token(idUsuario, Rol.CLIENTE, null);
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

    protected ResultActions comprar(UUID idUsuario, UUID idCompra, UUID idViaje, Integer... asientos) throws Exception {
        return mockMvc.perform(conToken(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/boletos/compras"), token(idUsuario))
                .contentType("application/json")
                .content(json(Map.of("idCompra", idCompra, "idViaje", idViaje, "asientos", List.of(asientos),
                        "fechaPago", "2026-10-20"))));
    }

    // ---------- Cartera simulada ----------

    protected void carteraAprueba() {
        carteraResponde(post(urlPathEqualTo(PAGOS)), 201,
                "{\"idPago\":\"" + UUID.randomUUID() + "\",\"saldoPosterior\":\"30.00\"}");
    }

    protected void carteraResponde(MappingBuilder peticion, int status, String cuerpo) {
        CARTERA.stubFor(peticion.willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json").withBody(cuerpo)));
    }

    protected void carteraTienePago(UUID idCompra, UUID idPago) {
        carteraResponde(get(urlPathEqualTo(PAGOS)).withQueryParam("idReferencia",
                        com.github.tomakehurst.wiremock.client.WireMock.equalTo(idCompra.toString())), 200,
                "{\"idPago\":\"" + idPago + "\",\"monto\":\"85.00\",\"fechaPago\":\"2026-10-20\"}");
    }

    protected int cobrosRecibidos() {
        return CARTERA.findAll(com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(urlPathEqualTo(PAGOS)))
                .size();
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

    /** Publica y espera a que el consumidor lo registre en {@code evento_procesado}. */
    protected void publicarYEsperar(TipoEvento tipo, SobreEvento<?> sobre) {
        publicar(tipo, sobre);
        esperar(tipo.nombre() + " " + sobre.idEvento(), () -> eventoProcesado(sobre.idEvento()));
    }

    /** Espera a que el consumidor termine (no hay Awaitility en el proyecto). */
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

    /** Viaje regular de Xela a la Capital, mañana, bus de 40 asientos y boleto de Q85.00. */
    protected DatosViaje datosViaje(UUID idViaje, EstadoViaje estado, OffsetDateTime salida) {
        return new DatosViaje(idViaje, TipoViaje.REGULAR, estado, UUID.randomUUID(), SUCURSAL_XELA, "Quetzaltenango",
                "Ciudad de Guatemala", null, salida, salida.plusHours(4), new BigDecimal("85.00"),
                new BusAsignado(UUID.randomUUID(), "P-123ABC", 40), new ChoferAsignado(UUID.randomUUID(), "Juan Pérez"));
    }

    protected OffsetDateTime manana() {
        return OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).withNano(0);
    }

    /** Viaje PROGRAMADO proyectado por el consumidor de ViajeProgramado. */
    protected UUID viajeALaVenta() {
        UUID idViaje = UUID.randomUUID();
        publicarYEsperar(TipoEvento.VIAJE_PROGRAMADO, sobre(TipoEvento.VIAJE_PROGRAMADO, idViaje, 0L,
                datosViaje(idViaje, EstadoViaje.PROGRAMADO, manana())));
        return idViaje;
    }

    /** Cliente proyectado por el consumidor de PerfilClienteActualizado. */
    protected UUID cliente(String nit) {
        UUID idUsuario = UUID.randomUUID();
        publicarPerfil(idUsuario, nit, 0L);
        return idUsuario;
    }

    protected void publicarPerfil(UUID idUsuario, String nit, long version) {
        publicarYEsperar(TipoEvento.PERFIL_CLIENTE_ACTUALIZADO, sobre(TipoEvento.PERFIL_CLIENTE_ACTUALIZADO, idUsuario,
                version, new DatosPerfilClienteActualizado(idUsuario, nit, "Carlos Méndez", true)));
    }

    /** NIT válido distinto en cada prueba (dígitos al azar + K). */
    protected static String nitNuevo() {
        return String.valueOf(1_000_000 + (long) (Math.random() * 8_999_999)) + "K";
    }

    protected String estadoCompra(UUID idCompra) {
        return jdbc.queryForObject("SELECT estado FROM compra WHERE id = ?", String.class, idCompra);
    }

    protected int compras(UUID idCompra) {
        return jdbc.queryForObject("SELECT count(*) FROM compra WHERE id = ?", Integer.class, idCompra);
    }

    protected int outbox(TipoEvento tipo, UUID idCompra) {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE tipo_evento = ? AND id_agregado = ?",
                Integer.class, tipo.nombre(), idCompra);
    }
}
