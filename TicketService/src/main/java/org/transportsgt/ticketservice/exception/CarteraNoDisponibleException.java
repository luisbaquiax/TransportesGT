package org.transportsgt.ticketservice.exception;

/**
 * Cartera no respondió (tiempo agotado, error 5xx o circuit breaker abierto). No se sabe si el cobro salió: la saga
 * deja la compra {@code RESERVADA} y el job de recuperación lo averigua.
 */
public class CarteraNoDisponibleException extends ServicioNoDisponibleException {

    public CarteraNoDisponibleException(String detalle) {
        super("SERVICIO_NO_DISPONIBLE", "El servicio de cartera no está disponible: " + detalle);
    }
}
