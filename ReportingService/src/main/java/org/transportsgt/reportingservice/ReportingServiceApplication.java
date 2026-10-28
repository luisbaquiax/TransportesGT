package org.transportsgt.reportingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.transportsgt.eventos.configuracion.EnableEventosComun;

/**
 * {@link EnableEventosComun}: consumidor idempotente, topología RabbitMQ (colas reportes.*.q y sus DLQ)
 * y {@code @EnableScheduling}. Reportes no publica eventos.
 */
@SpringBootApplication
@EnableEventosComun
public class ReportingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportingServiceApplication.class, args);
    }

}
