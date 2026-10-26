package org.transportsgt.privaterentalservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Pago de un alquiler cotizado (en línea con cartera o en efectivo en mostrador). */
public record PagoAlquilerRequest(
        @Schema(example = "2026-10-20") @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaPago
) {
}
