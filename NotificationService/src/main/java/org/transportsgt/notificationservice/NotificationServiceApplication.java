package org.transportsgt.notificationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.transportsgt.eventos.EnableEventosComun;

/**
 * {@link EnableEventosComun}: consumidor idempotente, topología RabbitMQ y {@code @EnableScheduling}. También
 * registra el outbox: este servicio no publica, pero el poller de la librería necesita la tabla {@code outbox_evento}.
 */
@SpringBootApplication
@EnableEventosComun
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }

}
