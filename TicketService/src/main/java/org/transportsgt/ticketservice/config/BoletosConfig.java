package org.transportsgt.ticketservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Activa {@link BoletosProperties}.
 */
@Configuration
@EnableConfigurationProperties(BoletosProperties.class)
public class BoletosConfig {
}
