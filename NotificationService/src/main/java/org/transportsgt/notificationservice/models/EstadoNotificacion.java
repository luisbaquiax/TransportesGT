package org.transportsgt.notificationservice.models;

/**
 * Estado de envío de una {@link Notificacion}. Tras N intentos fallidos queda FALLIDA.
 */
public enum EstadoNotificacion {
    PENDIENTE,
    ENVIADA,
    FALLIDA
}
