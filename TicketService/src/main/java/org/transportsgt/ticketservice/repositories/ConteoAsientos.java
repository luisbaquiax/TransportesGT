package org.transportsgt.ticketservice.repositories;

import java.util.UUID;

/**
 * Asientos ocupados ({@code RESERVADO} o {@code CONFIRMADO}) de un viaje.
 */
public interface ConteoAsientos {

    UUID getIdViaje();

    long getOcupados();
}
