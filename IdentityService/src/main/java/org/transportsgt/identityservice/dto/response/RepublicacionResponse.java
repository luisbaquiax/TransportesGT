package org.transportsgt.identityservice.dto.response;

/**
 * Cantidad de eventos guardados en el outbox por la republicación.
 */
public record RepublicacionResponse(
        int sucursales,
        int usuarios
) {
}
