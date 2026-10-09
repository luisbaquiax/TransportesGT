package org.transportsgt.privaterentalservice.models;

/**
 * Ciclo de vida de un {@link Alquiler}: SOLICITADO → COTIZADO → CONFIRMADO → FINALIZADO, o CANCELADO.
 * COTIZADO puede repetirse (el administrador reajusta el precio). Solo se paga en COTIZADO.
 */
public enum EstadoAlquiler {
    SOLICITADO,
    COTIZADO,
    CONFIRMADO,
    FINALIZADO,
    CANCELADO
}
