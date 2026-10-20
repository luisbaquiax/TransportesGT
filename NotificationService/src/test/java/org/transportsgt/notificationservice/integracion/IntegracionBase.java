package org.transportsgt.notificationservice.integracion;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.extension.RegisterExtension;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.transportsgt.eventos.ConvertidorEventos;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.TopologiaEventos;
import org.transportsgt.eventos.payload.DatosUsuario;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.notificationservice.services.envio.EnvioCorreosService;

import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Base de las pruebas de integración: contexto completo, contenedores, GreenMail como servidor SMTP, JWT firmados
 * como los de Identidad, publicación de eventos en RabbitMQ y espera hasta que el consumidor los procese.
 * Los jobs programados están apagados: las pruebas los ejecutan a mano. Las rutas van sin el context-path.
 */
@SpringBootTest(properties = {
        // Independiente del .env local
        "SPRING_DATASOURCE_URL=jdbc:postgresql://ignorado:5432/ignorado",
        "SPRING_DATASOURCE_USERNAME=ignorado",
        "SPRING_DATASOURCE_PASSWORD=ignorado",
        "RABBITMQ_URL=amqp://ignorado:5672",
        "jwt.secret=" + IntegracionBase.SECRETO_JWT,
        "MAIL_HOST=localhost",
        "MAIL_PORT=3025",
        "MAIL_SMTP_AUTH=false",
        // Sin credenciales: con usuario y contraseña JavaMail se autentica aunque auth=false, y GreenMail lo rechaza
        "MAIL_USERNAME=",
        "MAIL_PASSWORD=",
        "FRONTEND_URL=https://app.transportes.gt",
        "spring.jpa.show-sql=false",
        "logging.level.org.transportsgt=INFO",
        // Reintentos cortos del consumidor para que los mensajes lleguen rápido a la DLQ
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms",
        "notificaciones.envio.intervalo-ms=3600000",
        "notificaciones.licencias.cron=-"
})
@AutoConfigureMockMvc
@Import(ContenedoresConfig.class)
public abstract class IntegracionBase {

    static final String SECRETO_JWT = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final Duration ESPERA_MAXIMA = Duration.ofSeconds(15);

    /** SMTP en memoria en el puerto 3025; se reinicia (vacío) en cada prueba. */
    @RegisterExtension
    static final GreenMailExtension SMTP = new GreenMailExtension(ServerSetupTest.SMTP);

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected RabbitTemplate rabbit;
    @Autowired
    protected ConvertidorEventos convertidor;
    @Autowired
    protected EnvioCorreosService envioCorreos;

    private final NimbusJwtEncoder codificador = new NimbusJwtEncoder(new ImmutableSecret<>(
            new SecretKeySpec(Base64.getDecoder().decode(SECRETO_JWT), "HmacSHA256")));

    // ---------- HTTP ----------

    /** Token como el que emite Identidad (mismos claims y emisor). */
    protected String token(Rol rol, UUID idSucursal) {
        Instant ahora = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("servicio-identidad")
                .subject(UUID.randomUUID().toString())
                .claim("correo", "admin@transportes.gt")
                .claim("nombre", "Administrador")
                .claim("rol", rol.name())
                .issuedAt(ahora)
                .expiresAt(ahora.plusSeconds(1800));
        if (idSucursal != null) {
            claims.claim("idSucursal", idSucursal.toString());
        }
        return codificador.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }

    protected String tokenSistema() {
        return token(Rol.ADMIN_SISTEMA, null);
    }

    protected static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, String token) {
        return peticion.header("Authorization", "Bearer " + token);
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

    /** Espera a que se cumpla la condición (no hay Awaitility en el proyecto). */
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

    /** Contacto creado por UsuarioCreado. */
    protected DatosUsuario contacto(Rol rol, UUID idSucursal, boolean activo) {
        UUID idUsuario = UUID.randomUUID();
        DatosUsuario datos = new DatosUsuario(idUsuario, "u." + idUsuario.toString().substring(0, 8) + "@transportes.gt",
                "Usuario " + idUsuario.toString().substring(0, 4), rol, idSucursal, activo);
        publicarYEsperar(TipoEvento.USUARIO_CREADO, sobre(TipoEvento.USUARIO_CREADO, idUsuario, 0L, datos));
        return datos;
    }

    protected int notificaciones(UUID idEvento) {
        return jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE id_evento_origen = ?", Integer.class, idEvento);
    }

    // ---------- Correo ----------

    /** Ejecuta el job de envío una vez (no corre solo en las pruebas). */
    protected void enviarPendientes() {
        envioCorreos.procesar();
    }

    protected List<MimeMessage> recibidos() {
        return List.of(SMTP.getReceivedMessages());
    }

    protected List<MimeMessage> recibidosPor(String correo) {
        return recibidos().stream().filter(m -> destinatario(m).equalsIgnoreCase(correo)).toList();
    }

    protected static String destinatario(MimeMessage mensaje) {
        try {
            return mensaje.getAllRecipients()[0].toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Texto decodificado de todas las partes (HTML y texto plano), sin quoted-printable. */
    protected static String contenido(Part parte) {
        try {
            Object cuerpo = parte.getContent();
            if (cuerpo instanceof Multipart multipart) {
                StringBuilder texto = new StringBuilder();
                for (int i = 0; i < multipart.getCount(); i++) {
                    BodyPart hija = multipart.getBodyPart(i);
                    texto.append(contenido(hija)).append('\n');
                }
                return texto.toString();
            }
            return String.valueOf(cuerpo);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
