package org.transportsgt.ticketservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.transportsgt.eventos.configuracion.EnableEventosComun;

/**
 * {@link EnableEventosComun}: outbox (RegistroOutbox + PublicadorOutbox), limpieza del outbox,
 * consumidor idempotente, topología RabbitMQ y {@code @EnableScheduling}.
 */
@SpringBootApplication
@EnableEventosComun
public class TicketServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketServiceApplication.class, args);
    }

}
