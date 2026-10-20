package org.transportsgt.ticketservice.dto.response;

/**
 * Resultado de la compra en línea. El controlador lo convierte en el status HTTP.
 *
 * @param tipo {@code CONFIRMADA} (201), {@code EXISTENTE} (200, idCompra repetido ya confirmado) o
 *             {@code PENDIENTE} (202, Cartera no respondió: la compra sigue RESERVADA y el job la resuelve)
 */
public record ResultadoCompra(CompraResponse compra, Tipo tipo) {

    public enum Tipo {
        CONFIRMADA,
        EXISTENTE,
        PENDIENTE
    }
}
