package org.transportsgt.privaterentalservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Nueva tarifa del precio estimado. Una segunda tarifa con la misma {@code vigenteDesde} reemplaza a la anterior. */
public record TarifaRequest(
        @Schema(example = "10.50", type = "string") @NotNull
        @DecimalMin(value = "0.00") @Digits(integer = 8, fraction = 2) BigDecimal precioPorKm,
        @Schema(example = "550.00", type = "string") @NotNull
        @DecimalMin(value = "0.00") @Digits(integer = 8, fraction = 2) BigDecimal precioPorDia,
        @Schema(example = "2026-12-01") @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vigenteDesde
) {
}
