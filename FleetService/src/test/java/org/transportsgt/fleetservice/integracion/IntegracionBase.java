package org.transportsgt.fleetservice.integracion;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
        // Independientes del .env local: los contenedores mandan (ServiceConnection)
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
        // Viajes no existe en las pruebas: desactivar un bus debe fallar cerrado (503)
        "viajes.url=http://127.0.0.1:9/v1/trips",
        "flota.fotos.directorio=target/fotos-test"
})
@AutoConfigureMockMvc
@Import(ContenedoresConfig.class)
public abstract class IntegracionBase {

    /** Mismo secreto del @SpringBootTest; solo se usa para firmar tokens de prueba. */
    protected static final String SECRETO_PRUEBAS = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    /** Sucursales sembradas por V2__datos_prueba.sql (las mismas de Identidad). */
    protected static final UUID XELA = UUID.fromString("10000000-0000-4000-8000-000000000001");
    protected static final UUID CAPITAL = UUID.fromString("10000000-0000-4000-8000-000000000002");
    protected static final UUID HUEHUE = UUID.fromString("10000000-0000-4000-8000-000000000003");

    protected static final String PLACA_SEMILLA = "P900ABC";
    protected static final String LICENCIA_SEMILLA = "LIC-GT-101";

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

    /** Token HS256 con los claims que usa {@code UsuarioAutenticado} (iss = servicio-identidad). */
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
                .claim("nombre", "Prueba Flota");
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

    /** Cabecera Authorization sobre cualquier builder (POST, multipart, ...). */
    protected static <B extends org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder<B>>
            B conToken(B peticion, String token) {
        return peticion.header("Authorization", "Bearer " + token);
    }

    /** Serializa a JSON; si ya es un String (text block) lo devuelve tal cual. */
    protected String json(Object cuerpo) {
        if (cuerpo instanceof String texto) {
            return texto;
        }
        return jsonMapper.writeValueAsString(cuerpo);
    }

    protected JsonNode leer(MvcResult resultado) throws Exception {
        return jsonMapper.readTree(resultado.getResponse().getContentAsString());
    }

    /** Placa/licencia única por prueba para no chocar con la semilla ni entre pruebas. */
    protected static String unico(String prefijo) {
        return prefijo + "." + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    protected String crearBus(String placa) throws Exception {
        MvcResult resultado = mockMvc.perform(conToken(post("/buses"), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"placa":"%s","marca":"Toyota","modelo":"Hiace","anioFabricacion":2020,
                                 "capacidadPasajeros":40,"kilometrajeActual":0}""".formatted(placa))))
                .andExpect(status().isCreated())
                .andReturn();
        return leer(resultado).get("id").asString();
    }

    protected String crearChofer(String numeroLicencia) throws Exception {
        MvcResult resultado = mockMvc.perform(conToken(post("/choferes"), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"nombreCompleto":"Chofer de Prueba","correo":"%s@transportes.gt",
                                 "telefono":"5555-0000","numeroLicencia":"%s","tipoLicencia":"B",
                                 "fechaVencimientoLicencia":"2028-01-01","salarioBaseViaje":"150.00"}"""
                                .formatted(numeroLicencia.toLowerCase(), numeroLicencia))))
                .andExpect(status().isCreated())
                .andReturn();
        return leer(resultado).get("id").asString();
    }

    /** Colas y bindings declarados por la librería de eventos (topología real). */
    protected org.springframework.amqp.core.Declarables topologia() {
        return contexto.getBean(org.springframework.amqp.core.Declarables.class);
    }

    /** Llave de ruteo real de la topología para un fragmento (p. ej. "salida"). */
    protected String rutaDe(String fragmento) {
        return topologia().getDeclarablesByType(Binding.class).stream()
                .map(Binding::getRoutingKey)
                .filter(k -> k.contains(fragmento))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No hay binding con " + fragmento));
    }

    /** Cola destino de una llave de ruteo (p. ej. la cola que consume salidas). */
    protected String colaDeRuta(String fragmento) {
        String ruta = rutaDe(fragmento);
        return topologia().getDeclarablesByType(Binding.class).stream()
                .filter(b -> b.getRoutingKey().equals(ruta))
                .map(Binding::getDestination)
                .findFirst()
                .orElseThrow();
    }

    /** Cola de fallidos (DLQ) asociada a una cola normal: sufijo .q → .dlq. */
    protected String colaFallidosDe(String cola) {
        return cola.replace(".q", ".dlq");
    }

    /** Espera activa (el consumidor es asíncrono). */
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

    protected String placaDe(UUID idBus) {
        return jdbc.queryForObject("SELECT placa FROM bus WHERE id = ?", String.class, idBus);
    }

    protected String estadoDe(UUID idBus) {
        return jdbc.queryForObject("SELECT estado_operativo FROM bus WHERE id = ?", String.class, idBus);
    }

    protected String kilometrajeDe(UUID idBus) {
        return jdbc.queryForObject("SELECT kilometraje_actual::text FROM bus WHERE id = ?", String.class, idBus);
    }
}
