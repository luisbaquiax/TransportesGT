package org.transportsgt.tripservice.integracion;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;
import java.util.function.Supplier;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base de las pruebas de integración: contexto completo, contenedores PostgreSQL + RabbitMQ
 * y utilidades HTTP/JWT. Las rutas van sin el context-path (MockMvc no lo aplica).
 */
@SpringBootTest(properties = {
        "SPRING_DATASOURCE_URL=jdbc:postgresql://ignorado:5432/ignorado",
        "SPRING_DATASOURCE_USERNAME=ignorado",
        "SPRING_DATASOURCE_PASSWORD=ignorado",
        "RABBITMQ_URL=amqp://ignorado:5672",
        "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "jwt.issuer=servicio-identidad",
        "spring.jpa.show-sql=false",
        "logging.level.org.transportsgt=INFO",
        "eventos.outbox.intervalo-ms=200",
        "eventos.outbox.lote=50",
        "seguridad.clave-interna=clave-interna-prueba",
        // Boletos no existe en las pruebas: cambiar el bus de un viaje regular falla cerrado (503)
        "boletos.url=http://127.0.0.1:9/v1/tickets"
})
@AutoConfigureMockMvc
@Import(ContenedoresConfig.class)
public abstract class IntegracionBase {

    protected static final String SECRETO_PRUEBAS = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    protected static final String CLAVE_INTERNA_PRUEBAS = "clave-interna-prueba";

    protected static final UUID XELA = UUID.fromString("10000000-0000-4000-8000-000000000001");
    protected static final UUID CAPITAL = UUID.fromString("10000000-0000-4000-8000-000000000002");
    protected static final UUID HUEHUE = UUID.fromString("10000000-0000-4000-8000-000000000003");

    protected static final UUID BUS_1 = UUID.fromString("20000000-0000-4000-8000-000000000001");
    protected static final UUID BUS_2 = UUID.fromString("20000000-0000-4000-8000-000000000002");
    protected static final UUID CHOFER_1 = UUID.fromString("30000000-0000-4000-8000-000000000001");
    protected static final UUID CHOFER_2 = UUID.fromString("30000000-0000-4000-8000-000000000002");
    protected static final UUID RUTA_XELA_CAPITAL = UUID.fromString("40000000-0000-4000-8000-000000000001");
    protected static final UUID VIAJE_SEMILLA = UUID.fromString("50000000-0000-4000-8000-000000000001");

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected JsonMapper jsonMapper;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected RabbitTemplate rabbitTemplate;
    @Autowired
    protected RabbitAdmin rabbitAdmin;
    @Autowired
    protected org.springframework.context.ApplicationContext contexto;

    /**
     * Buses y choferes propios de las pruebas, independientes de la semilla V2 (que usa los ids reales de Flota y
     * puede cambiar): licencias largas y choferes enlazados a usuarios (e0000000-...) para probar el rol CHOFER.
     */
    @BeforeEach
    void fixturesDeFlota() {
        jdbc.update("""
                INSERT INTO bus_ref (id, id_sucursal, placa, capacidad_pasajeros, estado_operativo, activo, version) VALUES
                 ('20000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 'P123ABC', 45, 'DISPONIBLE', TRUE, 1),
                 ('20000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001', 'P456DEF', 30, 'DISPONIBLE', TRUE, 1),
                 ('20000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000002', 'P789GHI', 45, 'DISPONIBLE', TRUE, 1)
                ON CONFLICT (id) DO NOTHING""");
        jdbc.update("""
                INSERT INTO chofer_ref (id, id_usuario, id_sucursal, nombre_completo, salario_base_viaje,
                                        fecha_vencimiento_licencia, activo, version) VALUES
                 ('30000000-0000-4000-8000-000000000001', 'e0000000-0000-4000-8000-000000000001',
                  '10000000-0000-4000-8000-000000000001', 'Juan Pérez', 150.00, '2030-12-31', TRUE, 1),
                 ('30000000-0000-4000-8000-000000000002', 'e0000000-0000-4000-8000-000000000002',
                  '10000000-0000-4000-8000-000000000001', 'María López', 140.00, '2030-06-30', TRUE, 1),
                 ('30000000-0000-4000-8000-000000000003', 'e0000000-0000-4000-8000-000000000003',
                  '10000000-0000-4000-8000-000000000002', 'Carlos Gómez', 160.00, '2030-03-15', TRUE, 1)
                ON CONFLICT (id) DO NOTHING""");
    }

    protected String token(org.transportsgt.eventos.payload.Rol rol, UUID idSucursal, UUID idUsuario) {
        SecretKey clave = new SecretKeySpec(Base64.getDecoder().decode(SECRETO_PRUEBAS), "HmacSHA256");
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(clave));
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(idUsuario == null ? UUID.randomUUID().toString() : idUsuario.toString())
                .issuer("servicio-identidad")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .claim("correo", "prueba@transportes.gt")
                .claim("rol", rol.name())
                .claim("nombre", "Prueba Viajes");
        if (idSucursal != null) {
            claims.claim("idSucursal", idSucursal.toString());
        }
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }

    protected String tokenAdminSucursal() {
        return token(org.transportsgt.eventos.payload.Rol.ADMIN_SUCURSAL, XELA, null);
    }

    protected String tokenAdminSistema() {
        return token(org.transportsgt.eventos.payload.Rol.ADMIN_SISTEMA, null, null);
    }

    protected String tokenCajero() {
        return token(org.transportsgt.eventos.payload.Rol.CAJERO, XELA, null);
    }

    /** Chofer 1 de la semilla: idUsuario = e0000000-...-0001. */
    protected String tokenChofer1() {
        return token(org.transportsgt.eventos.payload.Rol.CHOFER, XELA,
                UUID.fromString("e0000000-0000-4000-8000-000000000001"));
    }

    protected <B extends org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder<B>>
            B conToken(B peticion, String token) {
        return peticion.header("Authorization", "Bearer " + token);
    }

    protected String json(Object cuerpo) {
        if (cuerpo instanceof String texto) {
            return texto;
        }
        return jsonMapper.writeValueAsString(cuerpo);
    }

    protected JsonNode leer(MvcResult resultado) throws Exception {
        return jsonMapper.readTree(resultado.getResponse().getContentAsString());
    }

    protected org.springframework.amqp.core.Declarables topologia() {
        return contexto.getBean(org.springframework.amqp.core.Declarables.class);
    }

    protected String rutaDe(String fragmento) {
        return topologia().getDeclarablesByType(Binding.class).stream()
                .map(Binding::getRoutingKey)
                .filter(k -> k.contains(fragmento))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No hay binding con " + fragmento));
    }

    protected String colaDeRuta(String fragmento) {
        String ruta = rutaDe(fragmento);
        return topologia().getDeclarablesByType(Binding.class).stream()
                .filter(b -> b.getRoutingKey().equals(ruta))
                .map(Binding::getDestination)
                .findFirst()
                .orElseThrow();
    }

    protected static void esperar(Supplier<Boolean> condicion, int segundosMaximos) {
        Instant limite = Instant.now().plusSeconds(segundosMaximos);
        while (Instant.now().isBefore(limite)) {
            if (Boolean.TRUE.equals(condicion.get())) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("La condición no se cumplió en " + segundosMaximos + " segundos");
    }

    protected Integer contarOutbox(UUID idAgregado, String tipoEvento) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM outbox_evento WHERE id_agregado = ? AND tipo_evento = ?",
                Integer.class, idAgregado, tipoEvento);
    }

    protected String estadoDe(UUID idViaje) {
        return jdbc.queryForObject("SELECT estado FROM viaje WHERE id = ?", String.class, idViaje);
    }

    /** Viaje regular en el futuro con bus y chofer libres (evita traslapes con la semilla). */
    protected String crearViajeRegularFuturo() throws Exception {
        OffsetDateTime salida = OffsetDateTime.now().plusDays(30);
        OffsetDateTime llegada = salida.plusHours(4);
        MvcResult resultado = mockMvc.perform(conToken(post("/viajes"), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"idRuta":"%s","idBus":"%s","idChofer":"%s",
                                 "fechaHoraSalida":"%s","fechaHoraLlegadaEstimada":"%s"}"""
                                .formatted(RUTA_XELA_CAPITAL, BUS_2, CHOFER_2, salida, llegada))))
                .andExpect(status().isCreated())
                .andReturn();
        return leer(resultado).get("id").asString();
    }
}
