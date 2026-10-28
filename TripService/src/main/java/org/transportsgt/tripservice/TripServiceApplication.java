package org.transportsgt.tripservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.transportsgt.eventos.configuracion.EnableEventosComun;

/**
 * {@link EnableEventosComun}: outbox (RegistroOutbox + PublicadorOutbox), limpieza del outbox,
 * topología RabbitMQ y {@code @EnableScheduling}.
 */
@SpringBootApplication
@EnableEventosComun
@ConfigurationPropertiesScan
public class TripServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TripServiceApplication.class, args);
    }

}
