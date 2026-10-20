package org.transportsgt.ticketservice.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Propiedades {@code boletos.*}: reglas configurables de la compra y del job de recuperación.
 *
 * @param compra       límites de una compra
 * @param reserva      vida de una reserva sin pago confirmado
 * @param recuperacion job que revisa las compras {@code RESERVADA}
 */
@Validated
@ConfigurationProperties(prefix = "boletos")
public record BoletosProperties(@Valid @NotNull Compra compra, @Valid @NotNull Reserva reserva,
                                @Valid @NotNull Recuperacion recuperacion) {

    /**
     * @param maxAsientos máximo de asientos por compra
     */
    public record Compra(@Min(1) @Max(60) int maxAsientos) {
    }

    /**
     * @param duracion tiempo hasta que una reserva sin pago se libera
     */
    public record Reserva(@NotNull Duration duracion) {
    }

    /**
     * @param antiguedad  antigüedad mínima de una compra {@code RESERVADA} para revisarla
     * @param intervaloMs cada cuánto corre el job
     */
    public record Recuperacion(@NotNull Duration antiguedad, @Min(1000) long intervaloMs) {
    }
}
