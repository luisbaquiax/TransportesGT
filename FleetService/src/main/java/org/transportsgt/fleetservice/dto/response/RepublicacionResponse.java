package org.transportsgt.fleetservice.dto.response;

/**
 * Cantidad de eventos guardados en el outbox por la republicación.
 */
public record RepublicacionResponse(
        int buses,
        int choferes
) {
}
