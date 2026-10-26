package org.transportsgt.ticketservice.dto.response;

import java.util.UUID;

/**
 * Mayor asiento {@code RESERVADO} o {@code CONFIRMADO} del viaje (0 si no hay). Lo usa Viajes antes de cambiar el bus.
 */
public record AsientoMaximoResponse(UUID idViaje, int asientoMaximo) {
}
