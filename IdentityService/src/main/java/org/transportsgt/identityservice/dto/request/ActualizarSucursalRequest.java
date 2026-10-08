package org.transportsgt.identityservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * @param version versión que el cliente leyó; si ya cambió se responde 409 (opcional)
 */
public record ActualizarSucursalRequest(
        @NotNull @Valid SucursalRequest sucursal,
        @Schema(example = "0", nullable = true) Long version
) {
}
