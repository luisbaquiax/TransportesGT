package org.transportsgt.notificationservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Propiedades {@code notificaciones.*}.
 *
 * @param frontendUrl     base de los enlaces de contraseña (variable {@code FRONTEND_URL})
 * @param rutaRestablecer ruta del frontend para RESTABLECIMIENTO
 * @param rutaActivar     ruta del frontend para ACTIVACION
 * @param remitente       {@code From} de los correos (variable {@code MAIL_REMITENTE})
 */
@ConfigurationProperties("notificaciones")
public record NotificacionesProperties(
        String frontendUrl,
        String rutaRestablecer,
        String rutaActivar,
        String remitente,
        Envio envio
) {

    /**
     * @param lote          correos por corrida del job
     * @param maxIntentos   al llegar a este número de fallos el correo queda FALLIDA
     * @param esperaInicial espera tras el primer fallo; se duplica en cada reintento
     */
    public record Envio(int lote, int maxIntentos, Duration esperaInicial) {
    }
}
