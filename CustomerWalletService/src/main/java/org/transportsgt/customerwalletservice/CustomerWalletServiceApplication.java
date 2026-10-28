package org.transportsgt.customerwalletservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.transportsgt.eventos.configuracion.EnableEventosComun;

/**
 * {@link EnableEventosComun}: outbox (RegistroOutbox + PublicadorOutbox), limpieza del outbox,
 * consumidor idempotente, topología RabbitMQ y {@code @EnableScheduling}.
 */
@SpringBootApplication
@EnableEventosComun
public class CustomerWalletServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerWalletServiceApplication.class, args);
    }

}
