package org.transportsgt.identityservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param version versión que el cliente leyó; si ya cambió se responde 409 (opcional)
 */
public record ActualizarUsuarioRequest(
        @Schema(example = "Ana María López")
        @NotBlank @Size(max = 150) String nombreCompleto,

        @Schema(example = "ana.lopez@transportes.gt")
        @NotBlank @Email @Size(max = 150) String correo,

        @Schema(example = "0", nullable = true) Long version
) {
}
