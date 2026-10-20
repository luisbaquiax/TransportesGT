package org.transportsgt.ticketservice.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Viaje regular a la venta con sus asientos disponibles.
 */
public record ViajeDisponibleResponse(
        UUID idViaje,
        UUID idRuta,
        UUID idSucursalOrigen,
        String nombreOrigen,
        String nombreDestino,
        OffsetDateTime fechaHoraSalida,
        OffsetDateTime fechaHoraLlegadaEstimada,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal precioBoleto,
        String placaBus,
        Short capacidad,
        int asientosDisponibles
) {
}
