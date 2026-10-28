package org.transportsgt.ticketservice.dto.response;

import org.transportsgt.eventos.payload.viajes.EstadoViaje;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Lista de pasajeros de un viaje para el abordaje.
 */
public record PasajerosViajeResponse(
        UUID idViaje,
        String nombreOrigen,
        String nombreDestino,
        OffsetDateTime fechaHoraSalida,
        String placaBus,
        EstadoViaje estado,
        int boletos,
        int abordados,
        List<PasajeroResponse> pasajeros
) {
}
