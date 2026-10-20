package org.transportsgt.notificationservice.models;

/**
 * Tipo de correo (columna {@code notificacion.tipo}). Cada uno tiene su plantilla en {@code templates/correo/}.
 */
public enum TipoNotificacion {
    COMPRA_BOLETOS,
    BOLETOS_ANULADOS,
    CARTERA_RECARGADA,
    CARTERA_REEMBOLSADA,
    ALQUILER_SOLICITADO,
    ALQUILER_COTIZADO,
    ALQUILER_CONFIRMADO,
    ALQUILER_CANCELADO,
    /**
     * Contiene un token secreto: se envía directo desde el consumidor y su cuerpo no se guarda.
     */
    ENLACE_CONTRASENA,
    CONTRASENA_CAMBIADA,
    LICENCIA_POR_VENCER
}
