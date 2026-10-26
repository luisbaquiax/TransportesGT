package org.transportsgt.customerwalletservice.dto.response;

/**
 * Cantidad de eventos guardados en el outbox por la republicación.
 */
public record RepublicacionResponse(
        int clientes
) {
}
