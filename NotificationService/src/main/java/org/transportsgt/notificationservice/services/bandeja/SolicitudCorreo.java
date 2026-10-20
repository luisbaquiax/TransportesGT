package org.transportsgt.notificationservice.services.bandeja;

import org.transportsgt.notificationservice.models.TipoNotificacion;

import java.util.Map;
import java.util.UUID;

/**
 * Un correo por encolar.
 *
 * @param variables      datos de la plantilla; {@code destinatario} y {@code fmt} se agregan solos
 * @param claveDedup     {@code evt:{idEvento}:{correo}} o {@code lic:{idRecordatorio}:{correo}}
 * @param idEventoOrigen nulo en los avisos de licencia (los genera el job, no un evento)
 */
public record SolicitudCorreo(
        TipoNotificacion tipo,
        Destinatario destinatario,
        Map<String, Object> variables,
        String claveDedup,
        UUID idEventoOrigen
) {
}
