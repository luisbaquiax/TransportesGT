package org.transportsgt.fleetservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Datos para registrar un bus. El estado nace DISPONIBLE y el kilometraje en 0 si no se envía.
 */
public record CrearBusRequest(
        @Schema(example = "P123XYZ", description = "Placa única (sin distinguir mayúsculas)")
        @NotBlank @Size(max = 15) String placa,

        @Schema(example = "Toyota")
        @NotBlank @Size(max = 50) String marca,

        @Schema(example = "Hiace")
        @NotBlank @Size(max = 50) String modelo,

        @Schema(example = "2019")
        @NotNull @Min(1980) @Max(2100) Short anioFabricacion,

        @Schema(example = "45")
        @NotNull @Min(1) @Max(600) Short capacidadPasajeros,

        @Schema(example = "12500.5", description = "Opcional; por omisión 0", nullable = true)
        @DecimalMin(value = "0") @Digits(integer = 8, fraction = 1) BigDecimal kilometrajeActual
) {
}
