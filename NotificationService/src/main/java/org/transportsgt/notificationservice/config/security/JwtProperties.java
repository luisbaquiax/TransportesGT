package org.transportsgt.notificationservice.config.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Propiedades {@code jwt.*}. La vigencia no aplica aquí: la fija Identidad al emitir.
 *
 * @param secret secreto HS256 en Base64 (variable {@code JWT_SECRET}), el mismo que usa Identidad
 * @param issuer valor esperado del claim {@code iss} (el que configura Identidad)
 */
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotBlank String issuer
) {
}
