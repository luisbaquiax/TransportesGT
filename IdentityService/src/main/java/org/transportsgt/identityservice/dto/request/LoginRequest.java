package org.transportsgt.identityservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(example = "admin@transportes.gt") @NotBlank @Email String correo,
        @Schema(example = "Admin1234") @NotBlank String contrasena
) {
}
