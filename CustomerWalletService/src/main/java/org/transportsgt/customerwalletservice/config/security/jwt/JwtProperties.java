package org.transportsgt.customerwalletservice.config.security.jwt;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
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
