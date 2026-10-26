package org.transportsgt.notificationservice.dto.response;

import org.transportsgt.notificationservice.services.envio.ResumenEnvio;
import org.transportsgt.notificationservice.services.licencia.ResumenAvisos;

/**
 * Resultado de {@code POST /notificaciones/interno/procesar}. Los avisos se procesan primero, así sus correos salen
 * en el mismo envío.
 */
public record ProcesamientoResponse(ResumenAvisos licencias, ResumenEnvio envio) {
}
