package org.transportsgt.customerwalletservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @param fechaRecarga fecha de negocio elegida por el usuario (valor inicial: hoy)
 */
public record RecargaRequest(
        @Schema(example = "200.00", type = "string")
        @NotNull @DecimalMin(value = "0.01")
        @Digits(integer = 10, fraction = 2)
        BigDecimal monto,
        @Schema(example = "2026-10-20") @NotNull
        LocalDate fechaRecarga
) {
}
