package org.transportsgt.ticketservice.models;

/**
 * Ciclo de vida de una {@link Compra}: RESERVADA → CONFIRMADA | ANULADA, CONFIRMADA → ANULADA.
 */
public enum EstadoCompra {
    RESERVADA,
    CONFIRMADA,
    ANULADA
}
