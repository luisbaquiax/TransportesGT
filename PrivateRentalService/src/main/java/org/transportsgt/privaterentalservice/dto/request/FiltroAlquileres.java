package org.transportsgt.privaterentalservice.dto.request;

import org.transportsgt.privaterentalservice.models.EstadoAlquiler;

import java.time.LocalDate;

/**
 * Filtros de GET /alquileres. {@code desde} y {@code hasta} son días de Guatemala (incluidos) sobre la salida;
 * {@code sinAsignar} lista los confirmados que aún no tienen bus.
 */
public record FiltroAlquileres(
        EstadoAlquiler estado,
        LocalDate desde,
        LocalDate hasta,
        Boolean sinAsignar
) {
}
