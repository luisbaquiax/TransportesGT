package org.transportsgt.customerwalletservice.models;

/**
 * Tipo de {@link MovimientoCartera}. Solo PAGO y REEMBOLSO llevan referencia (compra o alquiler).
 */
public enum TipoMovimiento {
    RECARGA,
    PAGO,
    REEMBOLSO
}
