package org.transportsgt.notificationservice.exception;

/**
 * 404: la notificación no existe.
 */
public class NotificacionNoEncontradaException extends RecursoNoEncontradoException {

    public NotificacionNoEncontradaException() {
        super("NO_ENCONTRADO", "La notificación no existe");
    }
}
