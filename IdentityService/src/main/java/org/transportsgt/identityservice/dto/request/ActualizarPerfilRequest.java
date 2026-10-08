package org.transportsgt.identityservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActualizarPerfilRequest(
        @Schema(example = "Ana María López")
        @NotBlank @Size(max = 150) String nombreCompleto
) {
}
