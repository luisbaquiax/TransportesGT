package org.transportsgt.fleetservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Outbox: la creación de un bus encola un evento que el publicador marca como
 * publicado; los eventos de Viajes (salida/llegada) se consumen con idempotencia
 * y un mensaje inválido termina en la cola de fallidos (DLQ).
 */
class EventosOutboxIntegrationTest extends IntegracionBase {

    @Test
    void crearBusPublicaBusRegistradoEnOutbox() throws Exception {
        String id = crearBus(unico("POUT"));
        esperar(() -> jdbc.queryForObject(
                "SELECT publicado_en IS NOT NULL FROM outbox_evento "
                        + "WHERE id_agregado = ? AND tipo_evento = 'BusRegistrado'",
                Boolean.class, UUID.fromString(id)), 15);
    }

    @Test
    void eventoDeViajeSeProcesaUnaSolaVez() throws Exception {
        String id = crearBus(unico("PIDEM"));
        UUID idViaje = UUID.randomUUID();
        UUID idEvento = UUID.randomUUID();
        String sobre = """
                {"idEvento":"%s","tipoEvento":"SalidaRegistrada","versionEvento":1,
                 "ocurridoEn":"2026-10-17T12:00:00Z","servicioProductor":"servicio-viajes",
                 "idAgregado":"%s","versionAgregado":1,"idCorrelacion":"%s",
                 "datos":{"idViaje":"%s","idBus":"%s","idChofer":"%s",
                          "fechaHoraSalidaReal":"2026-10-17T12:00:00-06:00",
                          "kilometrajeInicial":400,"idUsuarioRegistro":"%s"}}
                """.formatted(idEvento, idViaje, UUID.randomUUID(), idViaje, id,
                UUID.fromString(crearChofer(unico("LICD"))), UUID.randomUUID());

        rabbitTemplate.convertAndSend("transporte.eventos", rutaDe("salida"), sobre);
        esperar(() -> "EN_TRANSITO".equals(estadoDe(UUID.fromString(id))), 15);
        esperar(() -> jdbc.queryForObject(
                "SELECT count(*) FROM evento_procesado WHERE id_evento = ?", Integer.class, idEvento) == 1, 10);

        // El mismo mensaje otra vez: la idempotencia lo descarta (misma versión)
        rabbitTemplate.convertAndSend("transporte.eventos", rutaDe("salida"), sobre);
        Thread.sleep(1500);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM evento_procesado WHERE id_evento = ?", Integer.class, idEvento))
                .isEqualTo(1);
    }

    @Test
    void mensajeInvalidoTerminaEnLaDlq() throws Exception {
        String cola = colaDeRuta("salida");
        String dlq = colaFallidosDe(cola);
        long enDlqAntes = rabbitAdmin.getQueueInfo(dlq).getMessageCount();

        Message mensaje = MessageBuilder
                .withBody("esto-no-es-un-evento".getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .build();
        rabbitTemplate.send("transporte.eventos", rutaDe("salida"), mensaje);

        esperar(() -> rabbitAdmin.getQueueInfo(dlq).getMessageCount() > enDlqAntes, 20);
    }

    @Test
    void eventoConTipoDesconocidoVaALaDlq() throws Exception {
        String dlq = colaFallidosDe(colaDeRuta("llegada"));
        long enDlqAntes = rabbitAdmin.getQueueInfo(dlq).getMessageCount();

        String desconocido = """
                {"idEvento":"%s","tipoEvento":"EstoNoExiste","versionEvento":1,
                 "ocurridoEn":"2026-10-17T12:00:00Z","servicioProductor":"servicio-viajes",
                 "idAgregado":"%s","versionAgregado":1,"idCorrelacion":"%s","datos":{}}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        rabbitTemplate.convertAndSend("transporte.eventos", rutaDe("llegada"), desconocido);

        esperar(() -> rabbitAdmin.getQueueInfo(dlq).getMessageCount() > enDlqAntes, 20);
    }
}
