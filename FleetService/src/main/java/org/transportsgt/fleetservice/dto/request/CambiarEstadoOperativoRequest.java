package org.transportsgt.fleetservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.transportsgt.eventos.payload.EstadoOperativo;

/**
 * Cambia el estado operativo: DISPONIBLE, EN_TALLER o FUERA_DE_SERVICIO.
 * EN_TRANSITO solo lo mueven la salida y la llegada de un viaje (409 si se intenta aquí).
 */
public record CambiarEstadoOperativoRequest(
        @Schema(example = "EN_TALLER")
        @NotNull EstadoOperativo estadoOperativo,

        @Schema(example = "1", description = "Versión actual; si ya no coincide, responde 409", nullable = true)
        Long version
) {
}
