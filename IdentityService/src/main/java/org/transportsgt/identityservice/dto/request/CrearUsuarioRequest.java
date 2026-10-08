package org.transportsgt.identityservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.transportsgt.eventos.payload.Rol;

import java.util.UUID;

/**
 * Alta de personal. ADMIN_SISTEMA crea ADMIN_SISTEMA o ADMIN_SUCURSAL; ADMIN_SUCURSAL crea CAJERO o CHOFER
 * en su sucursal (si omite idSucursal se usa la suya).
 */
public record CrearUsuarioRequest(
        @Schema(example = "Ana López")
        @NotBlank @Size(max = 150) String nombreCompleto,

        @Schema(example = "cajero.xela@transportes.gt")
        @NotBlank @Email @Size(max = 150) String correo,

        @Schema(example = "CAJERO")
        @NotNull Rol rol,

        @Schema(description = "Obligatoria para ADMIN_SUCURSAL; nula para ADMIN_SISTEMA", nullable = true,
                example = "7b1f0c1e-8f43-4b8e-9a51-2d8f8c0e9a11")
        UUID idSucursal
) {
}
