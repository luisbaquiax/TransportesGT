package org.transportsgt.identityservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CambiarContrasenaRequest(
        @Schema(example = "Admin1234") @NotBlank String contrasenaActual,
        @Schema(example = "NuevaClave2026")
        @NotBlank @Pattern(regexp = PoliticaContrasena.REGEX, message = PoliticaContrasena.MENSAJE) String nuevaContrasena
) {
}
