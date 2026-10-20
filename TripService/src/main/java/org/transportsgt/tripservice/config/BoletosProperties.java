package org.transportsgt.tripservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** URL del servicio de Boletos (cuarta llamada síncrona del catálogo). */
@ConfigurationProperties(prefix = "boletos")
public record BoletosProperties(String url) {
}
