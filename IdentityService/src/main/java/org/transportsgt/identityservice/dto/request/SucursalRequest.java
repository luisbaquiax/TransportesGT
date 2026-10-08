package org.transportsgt.identityservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Datos editables de una sucursal (crear y actualizar).
 */
public record SucursalRequest(
        @Schema(example = "Quetzaltenango Centro")
        @NotBlank @Size(max = 100) String nombre,

        @Schema(example = "Quetzaltenango")
        @NotBlank @Size(max = 50) String departamento,

        @Schema(example = "4a. Calle 12-35, Zona 1")
        @NotBlank @Size(max = 250) String direccion,

        @Schema(example = "77651234", nullable = true)
        @Size(max = 20) @Pattern(regexp = "^[0-9+\\- ]*$", message = "solo puede contener dígitos, espacios, + y -")
        String telefono,

        @Schema(example = "14.834500")
        @NotNull @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 6) BigDecimal latitud,

        @Schema(example = "-91.518600")
        @NotNull @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 6) BigDecimal longitud
) {
}
