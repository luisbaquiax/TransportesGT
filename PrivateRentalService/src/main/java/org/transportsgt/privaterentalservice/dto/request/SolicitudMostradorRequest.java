package org.transportsgt.privaterentalservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * Solicitud presencial (mostrador): misma que la en línea más el cliente del mostrador.
 * NIT vacío se normaliza a {@code CF}.
 */
public record SolicitudMostradorRequest(
        @Valid SolicitudAlquilerRequest solicitud,
        @Schema(example = "1234567-8") @Size(max = 20) String nitCliente,
        @Schema(example = "Juan Pérez") @Size(max = 150) String nombreCliente
) {
}
