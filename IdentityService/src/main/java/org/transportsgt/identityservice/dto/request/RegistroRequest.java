package org.transportsgt.identityservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Autorregistro de clientes (rol CLIENTE).
 */
public record RegistroRequest(
        @Schema(example = "Carlos Méndez") @NotBlank @Size(max = 150) String nombreCompleto,
        @Schema(example = "carlos.mendez@gmail.com") @NotBlank @Email @Size(max = 150) String correo,
        @Schema(example = "Cliente1234")
        @NotBlank @Pattern(regexp = PoliticaContrasena.REGEX, message = PoliticaContrasena.MENSAJE) String contrasena
) {
}
