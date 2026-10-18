package org.transportsgt.privaterentalservice.integracion;

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
        // Cartera no existe en las pruebas: el pago en línea debe fallar cerrado (503)
        "cartera.url=http://127.0.0.1:9/v1/customers",
        "alquileres.factor-carretera=1.3"
})
@AutoConfigureMockMvc
@Import(ContenedoresConfig.class)
public abstract class IntegracionBase {

    protected static final String SECRETO_PRUEBAS = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    protected static final UUID XELA = UUID.fromString("10000000-0000-4000-8000-000000000001");
    protected static final UUID CAPITAL = UUID.fromString("10000000-0000-4000-8000-000000000002");
    protected static final UUID HUEHUE = UUID.fromString("10000000-0000-4000-8000-000000000003");

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

    protected String token(org.transportsgt.eventos.payload.Rol rol, UUID idSucursal) {
        SecretKey clave = new SecretKeySpec(Base64.getDecoder().decode(SECRETO_PRUEBAS), "HmacSHA256");
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(clave));
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(UUID.randomUUID().toString())
                .issuer("servicio-identidad")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .claim("correo", "prueba@transportes.gt")
                .claim("rol", rol.name())
                .claim("nombre", "Prueba Alquileres");
        if (idSucursal != null) {
            claims.claim("idSucursal", idSucursal.toString());
        }
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }

    protected String tokenCliente() {
        return token(org.transportsgt.eventos.payload.Rol.CLIENTE, null);
    }

    protected String tokenClienteConPerfil(UUID idUsuario, UUID idSucursal) {
        SecretKey clave = new SecretKeySpec(Base64.getDecoder().decode(SECRETO_PRUEBAS), "HmacSHA256");
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(clave));
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(idUsuario.toString())
                .issuer("servicio-identidad")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .claim("correo", "cliente@transportes.gt")
                .claim("rol", org.transportsgt.eventos.payload.Rol.CLIENTE.name())
                .claim("nombre", "Cliente Prueba");
        if (idSucursal != null) {
            claims.claim("idSucursal", idSucursal.toString());
        }
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }

    protected String tokenAdminSucursal() {
        return token(org.transportsgt.eventos.payload.Rol.ADMIN_SUCURSAL, XELA);
    }

    protected String tokenAdminSistema() {
        return token(org.transportsgt.eventos.payload.Rol.ADMIN_SISTEMA, null);
    }

    protected String tokenCajero() {
        return token(org.transportsgt.eventos.payload.Rol.CAJERO, XELA);
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

    /** Alquiler de prueba en línea, cotizado a 2700, listo para pagar. */
    protected String cotizar(String idAlquiler, String token) throws Exception {
        MvcResult resultado = mockMvc.perform(conToken(
                        post("/alquileres/%s/cotizacion".formatted(idAlquiler)), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"precioFinal":"2700.00",
                                 "fechaHoraLlegadaEstimada":"%s",
                                 "comentario":"Incluye peajes"}""".formatted(
                                OffsetDateTime.now().plusDays(10).plusHours(5)))))
                .andExpect(status().isOk())
                .andReturn();
        return leer(resultado).get("estado").asString();
    }

    protected String solicitar(String token) throws Exception {
        MvcResult resultado = mockMvc.perform(conToken(post("/alquileres/solicitudes"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"idSucursalOrigen":"%s","origen":"Quetzaltenango",
                                 "destino":"Ciudad de Guatemala",
                                 "fechaHoraSalida":"%s","idaYVuelta":false,
                                 "numeroPasajeros":40,"distanciaEstimadaKm":"200.00"}"""
                                .formatted(XELA, OffsetDateTime.now().plusDays(10)))))
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

    protected String estadoDe(UUID idAlquiler) {
        return jdbc.queryForObject("SELECT estado FROM alquiler WHERE id = ?", String.class, idAlquiler);
    }
}
