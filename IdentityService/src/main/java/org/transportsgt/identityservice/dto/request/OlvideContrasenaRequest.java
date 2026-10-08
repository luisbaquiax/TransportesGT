package org.transportsgt.identityservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record OlvideContrasenaRequest(
        @Schema(example = "cajero.xela@transportes.gt") @NotBlank @Email String correo
) {
}
