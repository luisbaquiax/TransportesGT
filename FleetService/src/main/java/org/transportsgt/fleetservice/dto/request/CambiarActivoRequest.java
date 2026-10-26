package org.transportsgt.fleetservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Activa o desactiva un bus. Para desactivar, Flota consulta a Viajes (409 BUS_CON_VIAJES si hay viajes).
 */
public record CambiarActivoRequest(
        @Schema(example = "false")
        @NotNull Boolean activo,

        @Schema(example = "1", description = "Versión actual; si ya no coincide, responde 409", nullable = true)
        Long version
) {
}
