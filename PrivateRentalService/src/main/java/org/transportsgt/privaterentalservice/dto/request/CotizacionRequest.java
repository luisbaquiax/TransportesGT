package org.transportsgt.privaterentalservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Ajuste del administrador: fija el precio final y la llegada estimada. */
public record CotizacionRequest(
        @Schema(example = "3800.00", type = "string") @NotNull
        @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal precioFinal,
        @Schema(example = "2026-11-10T14:30:00-06:00") @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fechaHoraLlegadaEstimada,
        @Schema(example = "Incluye peajes") @Size(max = 250) String comentario
) {
}
