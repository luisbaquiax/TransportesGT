package org.transportsgt.ticketservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Compra en línea. El NIT y el nombre del comprador salen de su perfil, nunca del cuerpo.
 *
 * @param idCompra  UUID generado por quien llama: es la clave de idempotencia de la compra y del cobro
 * @param fechaPago fecha de negocio elegida por el usuario (valor inicial: hoy)
 */
public record CompraRequest(
        @Schema(example = "6f1c2a7e-3b4d-4e5f-8a9b-0c1d2e3f4a5b") @NotNull UUID idCompra,
        @Schema(example = "5d1c2a7e-3b4d-4e5f-8a9b-0c1d2e3f4a5b") @NotNull UUID idViaje,
        @Schema(example = "[12, 13]") @NotEmpty List<@NotNull Integer> asientos,
        @Schema(example = "2026-10-20") @NotNull LocalDate fechaPago
) {
}
