package org.transportsgt.notificationservice.integracion;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.transportsgt.eventos.contrato.Productor;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.mensajeria.rabbit.TopologiaEventos;
import org.transportsgt.eventos.payload.boletos.Canal;
import org.transportsgt.eventos.payload.alquileres.DatosAlquilerSolicitado;
import org.transportsgt.eventos.payload.cartera.DatosCarteraRecargada;
import org.transportsgt.eventos.payload.identidad.DatosUsuario;
import org.transportsgt.eventos.payload.boletos.DatosVentaBoletos;
import org.transportsgt.eventos.payload.identidad.Rol;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Patrón de correo: el consumidor encola PENDIENTE y el job de envío lo manda por SMTP.
 */
class CorreosIT extends IntegracionBase {

    private DatosVentaBoletos venta(UUID idComprador) {
        return new DatosVentaBoletos(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                idComprador == null ? Canal.MOSTRADOR : Canal.EN_LINEA, List.of(12, 13), new BigDecimal("85.00"),
                new BigDecimal("170.00"), idComprador, "CF", "Consumidor final", null, LocalDate.parse("2026-10-20"),
                UUID.randomUUID(), UUID.randomUUID());
    }

    private DatosCarteraRecargada recarga(UUID idUsuario) {
        return new DatosCarteraRecargada(UUID.randomUUID(), UUID.randomUUID(), idUsuario, new BigDecimal("200.00"),
                new BigDecimal("350.00"), LocalDate.parse("2026-10-20"));
    }

    @Test
    void ventaBoletos_sinComprador_noGeneraCorreo() {
        // Arrange
        SobreEvento<DatosVentaBoletos> evento = sobre(TipoEvento.VENTA_BOLETOS_REGISTRADA, UUID.randomUUID(), 1L,
                venta(null));

        // Act
        publicarYEsperar(TipoEvento.VENTA_BOLETOS_REGISTRADA, evento);

        // Assert
        assertEquals(0, notificaciones(evento.idEvento()));
    }

    @Test
    void ventaBoletos_conComprador_encolaPendienteYElJobLoEnvia() throws Exception {
        // Arrange
        DatosUsuario comprador = contacto(Rol.CLIENTE, null, true);
        SobreEvento<DatosVentaBoletos> evento = sobre(TipoEvento.VENTA_BOLETOS_REGISTRADA, UUID.randomUUID(), 1L,
                venta(comprador.idUsuario()));

        // Act
        publicarYEsperar(TipoEvento.VENTA_BOLETOS_REGISTRADA, evento);
        Map<String, Object> pendiente = jdbc.queryForMap("""
                SELECT tipo, estado, clave_dedup, correo_destino FROM notificacion WHERE id_evento_origen = ?
                """, evento.idEvento());
        enviarPendientes();

        // Assert
        assertAll(
                () -> assertEquals("COMPRA_BOLETOS", pendiente.get("tipo")),
                () -> assertEquals("PENDIENTE", pendiente.get("estado")),
                () -> assertEquals("evt:" + evento.idEvento() + ":" + comprador.correo(), pendiente.get("clave_dedup")),
                () -> assertEquals("ENVIADA", jdbc.queryForObject(
                        "SELECT estado FROM notificacion WHERE id_evento_origen = ?", String.class, evento.idEvento())),
                () -> assertEquals("Confirmación de compra de boletos",
                        recibidosPor(comprador.correo()).getFirst().getSubject())
        );
    }

    @Test
    void carteraRecargada_envíaElCorreoConElSaldoPosterior() throws Exception {
        // Arrange
        DatosUsuario cliente = contacto(Rol.CLIENTE, null, true);

        // Act
        publicarYEsperar(TipoEvento.CARTERA_RECARGADA, sobre(TipoEvento.CARTERA_RECARGADA, UUID.randomUUID(), 1L,
                recarga(cliente.idUsuario())));
        enviarPendientes();

        // Assert
        MimeMessage correo = recibidosPor(cliente.correo()).getFirst();
        String texto = contenido(correo);
        assertAll(
                () -> assertEquals("Recarga de cartera acreditada", correo.getSubject()),
                () -> assertTrue(texto.contains("Q 200.00")),
                () -> assertTrue(texto.contains("Q 350.00")),
                () -> assertTrue(texto.contains("Este es un correo automático"))
        );
    }

    @Test
    void alquilerSolicitado_cuandoLaSucursalTieneAdmins_avisaSoloALosAdminsActivos() throws Exception {
        // Arrange
        UUID sucursal = UUID.randomUUID();
        DatosUsuario admin1 = contacto(Rol.ADMIN_SUCURSAL, sucursal, true);
        DatosUsuario admin2 = contacto(Rol.ADMIN_SUCURSAL, sucursal, true);
        DatosUsuario cajero = contacto(Rol.CAJERO, sucursal, true);
        OffsetDateTime salida = OffsetDateTime.parse("2026-11-10T07:00:00-06:00");
        DatosAlquilerSolicitado solicitud = new DatosAlquilerSolicitado(UUID.randomUUID(), null, "Ana López", "CF",
                sucursal, "Quetzaltenango", "Antigua Guatemala", salida, null, 30, new BigDecimal("3500.00"));

        // Act
        publicarYEsperar(TipoEvento.ALQUILER_SOLICITADO, sobre(TipoEvento.ALQUILER_SOLICITADO, solicitud.idAlquiler(),
                0L, solicitud));
        enviarPendientes();

        // Assert
        assertAll(
                () -> assertEquals(1, recibidosPor(admin1.correo()).size()),
                () -> assertEquals(1, recibidosPor(admin2.correo()).size()),
                () -> assertEquals(0, recibidosPor(cajero.correo()).size()),
                () -> assertTrue(contenido(recibidosPor(admin1.correo()).getFirst()).contains("Q 3,500.00")),
                () -> assertTrue(contenido(recibidosPor(admin1.correo()).getFirst()).contains("10/11/2026 07:00"))
        );
    }

    @Test
    void carteraRecargada_cuandoElUsuarioNoTieneContacto_seReintentaYTerminaEnLaDlq() {
        // Arrange
        SobreEvento<DatosCarteraRecargada> evento = sobre(TipoEvento.CARTERA_RECARGADA, UUID.randomUUID(), 1L,
                recarga(UUID.randomUUID()));

        // Act
        publicar(TipoEvento.CARTERA_RECARGADA, evento);

        // Assert
        Message enDlq = rabbit.receive(TopologiaEventos.colaFallidos("notificaciones", Productor.CARTERA), 15_000);
        assertNotNull(enDlq, "El evento sin contacto debía llegar a la DLQ tras los reintentos");
        assertAll(
                () -> assertTrue(new String(enDlq.getBody(), StandardCharsets.UTF_8).contains(evento.idEvento().toString())),
                // Agotó los reintentos (rechazado), no fue directo a la DLQ como un evento no recuperable
                () -> assertNotNull(enDlq.getMessageProperties().getHeader("x-death")),
                () -> assertFalse(eventoProcesado(evento.idEvento())),
                () -> assertEquals(0, notificaciones(evento.idEvento()))
        );
    }

    @Test
    void mensaje_cuandoNoEsJson_vaDirectoALaDlq() {
        // Arrange
        String cuerpo = "{ esto no es JSON " + UUID.randomUUID();

        // Act
        publicarCrudo(TipoEvento.VENTA_BOLETOS_REGISTRADA.llaveRuteo(), cuerpo.getBytes(StandardCharsets.UTF_8));

        // Assert
        Message enDlq = rabbit.receive(TopologiaEventos.colaFallidos("notificaciones", Productor.BOLETOS), 15_000);
        assertNotNull(enDlq, "El mensaje inválido debía llegar a la DLQ de boletos");
        assertAll(
                () -> assertEquals(cuerpo, new String(enDlq.getBody(), StandardCharsets.UTF_8)),
                () -> assertTrue(enDlq.getMessageProperties().getHeader("x-motivo-fallo").toString()
                        .startsWith("JSON inválido"))
        );
    }
}
