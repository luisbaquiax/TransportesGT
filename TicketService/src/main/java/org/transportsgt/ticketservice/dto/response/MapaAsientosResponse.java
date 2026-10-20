package org.transportsgt.ticketservice.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * @param ocupados asientos {@code RESERVADO} o {@code CONFIRMADO}, de menor a mayor
 */
public record MapaAsientosResponse(UUID idViaje, int capacidad, List<Integer> ocupados) {
}
