package org.transportsgt.ticketservice.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Boleto abordado. Si ya lo estaba se devuelve el registro original.
 */
public record AbordajeResponse(
        String codigo,
        UUID idViaje,
        Short numeroAsiento,
        String nombreComprador,
        OffsetDateTime abordadoEn
) {
}
