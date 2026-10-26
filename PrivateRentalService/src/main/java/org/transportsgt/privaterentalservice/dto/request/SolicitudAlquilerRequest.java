package org.transportsgt.privaterentalservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Solicitud de alquiler en línea o en mostrador (compartida).
 * Con coordenadas (las 4 juntas) el backend calcula la distancia; sin ellas, el solicitante la indica.
 */
public record SolicitudAlquilerRequest(
        @NotNull UUID idSucursalOrigen,
        @Schema(example = "Quetzaltenango") @NotNull @Size(max = 150) String origen,
        @Schema(example = "Ciudad de Guatemala") @NotNull @Size(max = 150) String destino,
        @Schema(example = "2026-11-10T08:00:00-06:00") @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fechaHoraSalida,
        @Schema(example = "false") Boolean idaYVuelta,
        @Schema(example = "2026-11-12T18:00:00-06:00")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fechaHoraRetorno,
        @Schema(example = "45") @NotNull @Min(1) @Max(300) Integer numeroPasajeros,
        @Schema(example = "14.8347", type = "number") @Digits(integer = 2, fraction = 6)
        BigDecimal latitudOrigen,
        @Schema(example = "-91.5181", type = "number") @Digits(integer = 3, fraction = 6)
        BigDecimal longitudOrigen,
        @Schema(example = "14.6427", type = "number") @Digits(integer = 2, fraction = 6)
        BigDecimal latitudDestino,
        @Schema(example = "-90.5133", type = "number") @Digits(integer = 3, fraction = 6)
        BigDecimal longitudDestino,
        @Schema(example = "205.50", type = "string")
        @DecimalMin(value = "0.01") @Digits(integer = 6, fraction = 2) BigDecimal distanciaEstimadaKm
) {
    public boolean esIdaYVuelta() {
        return Boolean.TRUE.equals(idaYVuelta);
    }

    public boolean tieneCoordenadas() {
        return latitudOrigen != null && longitudOrigen != null
                && latitudDestino != null && longitudDestino != null;
    }
}
