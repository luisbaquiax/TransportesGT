package org.transportsgt.notificationservice.correo;

/**
 * Puerto de salida del correo. Los jobs y el envío del enlace dependen de esta interfaz, no de JavaMail.
 */
public interface EnviadorCorreo {

    /**
     * @throws EnvioCorreoException si el servidor no aceptó el correo
     */
    void enviar(MensajeCorreo mensaje);
}
