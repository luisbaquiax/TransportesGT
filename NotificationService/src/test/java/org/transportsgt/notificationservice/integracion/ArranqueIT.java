package org.transportsgt.notificationservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.transportsgt.eventos.Productor;
import org.transportsgt.eventos.TopologiaEventos;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ArranqueIT extends IntegracionBase {

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Test
    void contexto_cuandoArranca_flywayCreaElEsquemaDelModelo() {
        // Act
        List<String> tablas = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                 WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history' ORDER BY 1
                """, String.class);

        // Assert: outbox_evento queda vacía, pero el poller de la librería la necesita
        assertEquals(List.of("chofer_licencia", "contacto", "evento_procesado", "notificacion", "outbox_evento",
                "recordatorio_licencia"), tablas);
    }

    @Test
    void contexto_cuandoArranca_declaraUnaColaYUnaDlqPorProductor() {
        // Act & Assert
        assertAll(List.of(Productor.IDENTIDAD, Productor.FLOTA, Productor.BOLETOS, Productor.CARTERA,
                        Productor.ALQUILERES).stream()
                .flatMap(p -> List.of(TopologiaEventos.cola("notificaciones", p),
                        TopologiaEventos.colaFallidos("notificaciones", p)).stream())
                .map(cola -> () -> assertNotNull(amqpAdmin.getQueueInfo(cola), "Falta la cola " + cola)));
        assertEquals("notificaciones.flota.q", TopologiaEventos.cola("notificaciones", Productor.FLOTA));
    }
}
