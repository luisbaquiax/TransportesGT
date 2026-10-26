package org.transportsgt.privaterentalservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.transportsgt.eventos.EnableEventosComun;

/**
 * {@link EnableEventosComun}: outbox (RegistroOutbox + PublicadorOutbox), limpieza del outbox,
 * topología RabbitMQ y {@code @EnableScheduling}.
 */
@SpringBootApplication
@EnableEventosComun
@ConfigurationPropertiesScan
public class PrivateRentalServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrivateRentalServiceApplication.class, args);
    }

}
