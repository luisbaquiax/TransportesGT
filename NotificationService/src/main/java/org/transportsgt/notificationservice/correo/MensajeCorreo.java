package org.transportsgt.notificationservice.correo;

/**
 * Correo listo para enviar. El texto plano alternativo se obtiene del HTML al enviar.
 */
public record MensajeCorreo(String destino, String asunto, String html) {
}
