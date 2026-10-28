package org.transportsgt.operationalcostsservice.integracion;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
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
        "eventos.outbox.lote=50"
})
@AutoConfigureMockMvc
@Import(ContenedoresConfig.class)
public abstract class IntegracionBase {

    protected static final String SECRETO_PRUEBAS = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    protected static final UUID XELA = UUID.fromString("10000000-0000-4000-8000-000000000001");
    protected static final UUID CAPITAL = UUID.fromString("10000000-0000-4000-8000-000000000002");
    protected static final UUID HUEHUE = UUID.fromString("10000000-0000-4000-8000-000000000003");
    protected static final UUID BUS_XELA = UUID.fromString("20000000-0000-4000-8000-000000000001");
    protected static final UUID CHOFER_XELA = UUID.fromString("30000000-0000-4000-8000-000000000001");
    protected static final UUID VIAJE_PRUEBA = UUID.fromString("40000000-0000-4000-8000-000000000001");

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

    protected String token(org.transportsgt.eventos.payload.identidad.Rol rol, UUID idSucursal) {
        SecretKey clave = new SecretKeySpec(Base64.getDecoder().decode(SECRETO_PRUEBAS), "HmacSHA256");
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(clave));
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(UUID.randomUUID().toString())
                .issuer("servicio-identidad")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .claim("correo", "prueba@transportes.gt")
                .claim("rol", rol.name())
                .claim("nombre", "Prueba Costos");
        if (idSucursal != null) {
            claims.claim("idSucursal", idSucursal.toString());
        }
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }

    protected String tokenAdminSucursal() {
        return token(org.transportsgt.eventos.payload.identidad.Rol.ADMIN_SUCURSAL, XELA);
    }

    protected String tokenAdminSistema() {
        return token(org.transportsgt.eventos.payload.identidad.Rol.ADMIN_SISTEMA, null);
    }

    protected static <B extends org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder<B>>
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

    /** Proyecta una sucursal y un bus en las proyecciones locales (como si llegaran los eventos). */
    protected void sembrarSucursalYBus() {
        jdbc.update("INSERT INTO sucursal_ref (id, nombre, activa, version) VALUES (?, 'Quetzaltenango', true, 1) "
                + "ON CONFLICT (id) DO UPDATE SET nombre = EXCLUDED.nombre, activa = EXCLUDED.activa, "
                + "version = EXCLUDED.version", XELA);
        jdbc.update("INSERT INTO bus_ref (id, id_sucursal, placa, activo, version) VALUES (?, ?, 'P100ABC', true, 1) "
                + "ON CONFLICT (id) DO UPDATE SET id_sucursal = EXCLUDED.id_sucursal, placa = EXCLUDED.placa, "
                + "activo = EXCLUDED.activo, version = EXCLUDED.version", BUS_XELA, XELA);
    }

    protected String solicitarGastoTaller() throws Exception {
        sembrarSucursalYBus();
        String idCategoria = jdbc.queryForObject(
                "SELECT id FROM categoria_gasto WHERE lower(nombre) = 'llantas' LIMIT 1", String.class);
        MvcResult resultado = mockMvc.perform(conToken(post("/costos/taller"), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"idBus":"%s","fechaServicio":"2026-10-15",
                                 "observaciones":"Cambio de llantas",
                                 "lineas":[{"idCategoria":"%s","descripcion":"4 llantas",
                                            "tipo":"REPUESTO","monto":"1500.00"},
                                           {"idCategoria":"%s","descripcion":"Mano de obra",
                                            "tipo":"MANO_DE_OBRA","monto":"350.00"}]}"""
                                .formatted(BUS_XELA, idCategoria, idCategoria))))
                .andExpect(status().isCreated())
                .andReturn();
        return leer(resultado).get("id").asString();
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

    protected String colaFallidosDe(String cola) {
        return cola.replace(".q", ".dlq");
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
}
