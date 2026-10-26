package org.transportsgt.ticketservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Código del boleto, leído del QR o escrito a mano (se ignoran mayúsculas y espacios).
 */
public record AbordajeRequest(
        @Schema(example = "BOL-7K3MPQ2A") @NotBlank @Size(max = 20) String codigo
) {
}
