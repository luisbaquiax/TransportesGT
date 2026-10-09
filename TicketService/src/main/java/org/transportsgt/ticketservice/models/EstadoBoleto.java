package org.transportsgt.ticketservice.models;

/**
 * Ciclo de vida de un {@link Boleto}. Un boleto ANULADO libera su asiento ({@code uq_asiento_activo}).
 */
public enum EstadoBoleto {
    RESERVADO,
    CONFIRMADO,
    ANULADO
}
