package org.transportsgt.identityservice.integracion;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL 16 y RabbitMQ reales (no H2: los triggers son de PostgreSQL).
 * Spring reutiliza el contexto, y con él los contenedores, entre todas las pruebas de integración.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ContenedoresConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("bd_identidad");
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbit() {
        return new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-alpine"));
    }
}
