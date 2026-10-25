package org.transportsgt.reportingservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Error TRANSITORIO de un consumidor: llegó un evento que depende de otro que aún no llega (p. ej.
 * BoletosAnulados antes que VentaBoletosRegistrada). DespachadorEventos la deja propagar, la transacción se
 * revierte y Spring AMQP reintenta con espera creciente; al agotarse, el mensaje va a la DLQ. No sale por HTTP.
 */
public class ProyeccionNoDisponibleException extends ReportingServiceException {

    public ProyeccionNoDisponibleException(String mensaje) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "PROYECCION_NO_DISPONIBLE", mensaje);
    }
}
