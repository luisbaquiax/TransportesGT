package org.transportsgt.notificationservice.correo;

import org.transportsgt.notificationservice.models.TipoNotificacion;

import java.util.Map;

/**
 * Convierte un tipo de correo y sus datos en asunto y HTML.
 */
public interface RenderizadorCorreo {

    CorreoRenderizado renderizar(TipoNotificacion tipo, Map<String, Object> variables);
}
