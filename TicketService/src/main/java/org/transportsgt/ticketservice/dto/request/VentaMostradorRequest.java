package org.transportsgt.ticketservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Venta presencial. Sin NIT se registra como consumidor final ({@code CF}).
 */
public record VentaMostradorRequest(
        @Schema(example = "9b8a7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d") @NotNull UUID idCompra,
        @Schema(example = "5d1c2a7e-3b4d-4e5f-8a9b-0c1d2e3f4a5b") @NotNull UUID idViaje,
        @Schema(example = "[20, 21]") @NotEmpty List<@NotNull Integer> asientos,
        @Schema(example = "1234567-K", description = "Opcional; se guarda normalizado") @Size(max = 25) String nitComprador,
        @Schema(example = "Carlos Méndez", description = "Opcional") @Size(max = 150) String nombreComprador,
        @Schema(example = "2026-10-20") @NotNull LocalDate fechaPago
) {
}
