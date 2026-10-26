package org.transportsgt.identityservice.config.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Propiedades {@code jwt.*}. La aplicación no arranca si la vigencia está fuera de 30–60 min.
 *
 * @param secret     secreto HS256 en Base64 (variable {@code JWT_SECRET}); mínimo 32 bytes decodificados
 * @param expiration vigencia del token de acceso
 * @param issuer     claim {@code iss}
 */
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotNull @DurationMin(minutes = 30) @DurationMax(minutes = 60) Duration expiration,
        @NotBlank String issuer
) {
}
