package org.transportsgt.identityservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Sirve para RESTABLECIMIENTO y para ACTIVACION (el propósito lo indica el token).
 */
public record RestablecerContrasenaRequest(
        @Schema(example = "q3xY0k1v9Q8hZ2bN4mR7sT6uW5pL0aE3cD1fG8jK2nM")
        @NotBlank @Size(max = 100) String token,
        @Schema(example = "NuevaClave2026")
        @NotBlank @Pattern(regexp = PoliticaContrasena.REGEX, message = PoliticaContrasena.MENSAJE) String nuevaContrasena
) {
}
