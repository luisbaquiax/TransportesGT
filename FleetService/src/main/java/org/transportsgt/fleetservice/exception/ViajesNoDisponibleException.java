package org.transportsgt.fleetservice.exception;

/**
 * 503 SERVICIO_NO_DISPONIBLE: Viajes no respondió (circuit breaker abierto o tiempo agotado).
 * La operación se rechaza: es más seguro negar el cambio que permitir uno incorrecto.
 */
public class ViajesNoDisponibleException extends ServicioNoDisponibleException {

    public ViajesNoDisponibleException() {
        super("SERVICIO_NO_DISPONIBLE",
                "El servicio de viajes no está disponible en este momento; intente de nuevo");
    }

    public ViajesNoDisponibleException(Throwable causa) {
        this();
        initCause(causa);
    }
}
