package org.transportsgt.notificationservice.services.bandeja;

/**
 * Bandeja de salida: los consumidores y el job de licencias no envían, encolan. El job de envío los manda después,
 * así una falla del SMTP nunca revierte la operación que originó el correo.
 */
public interface BandejaSalida {

    /**
     * Renderiza el correo y lo guarda {@code PENDIENTE}, listo para enviar ya. Debe llamarse dentro de la transacción
     * del cambio que lo origina.
     *
     * @return {@code false} si ya existía un correo con esa {@code claveDedup} (evento reprocesado)
     */
    boolean encolar(SolicitudCorreo solicitud);
}
