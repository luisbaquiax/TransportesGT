package org.transportsgt.identityservice.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Propiedades {@code identidad.*}.
 *
 * @param vigenciaRestablecimiento vigencia del enlace "olvidé mi contraseña" (30 min)
 * @param vigenciaActivacion       vigencia del enlace de activación de cuenta (48 h)
 * @param maxSolicitudesPorHora    solicitudes de restablecimiento por usuario y hora (3)
 * @param retencionTokens          tiempo que se conservan los tokens vencidos antes de borrarlos (7 días)
 */
@Validated
@ConfigurationProperties(prefix = "identidad")
public record IdentidadProperties(
        @NotNull Duration vigenciaRestablecimiento,
        @NotNull Duration vigenciaActivacion,
        @Positive int maxSolicitudesPorHora,
        @NotNull Duration retencionTokens
) {
}
