package org.transportsgt.identityservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Primer administrador de una sucursal. Recibe un enlace de activación para definir su contraseña.
 */
public record NuevoAdministradorRequest(
        @Schema(example = "María Fernanda Pérez")
        @NotBlank @Size(max = 150) String nombreCompleto,

        @Schema(example = "admin.xela@transportes.gt")
        @NotBlank @Email @Size(max = 150) String correo
) {
}
