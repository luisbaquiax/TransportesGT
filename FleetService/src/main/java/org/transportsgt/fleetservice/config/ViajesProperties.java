package org.transportsgt.fleetservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * URL base del servicio de Viajes para las llamadas síncronas (plan §2.6).
 */
@ConfigurationProperties(prefix = "viajes")
public record ViajesProperties(String url) {
}
