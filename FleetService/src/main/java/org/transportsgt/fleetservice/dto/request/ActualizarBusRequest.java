package org.transportsgt.fleetservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Datos editables de un bus. No incluye estado operativo ni kilometraje:
 * esos solo los cambian los endpoints propios o los eventos de Viajes.
 */
public record ActualizarBusRequest(
        @Schema(example = "P123XYZ")
        @NotBlank @Size(max = 15) String placa,

        @Schema(example = "Toyota")
        @NotBlank @Size(max = 50) String marca,

        @Schema(example = "Hiace")
        @NotBlank @Size(max = 50) String modelo,

        @Schema(example = "2019")
        @NotNull @Min(1980) @Max(2100) Short anioFabricacion,

        @Schema(example = "45")
        @NotNull @Min(1) @Max(600) Short capacidadPasajeros,

        @Schema(example = "1", description = "Versión actual; si ya no coincide, responde 409", nullable = true)
        Long version
) {
}
