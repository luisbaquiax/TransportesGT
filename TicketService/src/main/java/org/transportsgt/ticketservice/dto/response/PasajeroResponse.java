package org.transportsgt.ticketservice.dto.response;

import org.transportsgt.eventos.payload.boletos.Canal;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un boleto confirmado de la lista de pasajeros; {@code abordadoEn} es null si aún no abordó.
 */
public record PasajeroResponse(
        Short numeroAsiento,
        String codigo,
        UUID idCompra,
        Canal canal,
        String nombreComprador,
        String nitComprador,
        OffsetDateTime abordadoEn
) {
}
