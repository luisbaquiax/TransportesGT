package org.transportsgt.notificationservice.services.envio;

/**
 * Envía los correos PENDIENTE cuyo próximo intento ya llegó.
 */
public interface EnvioCorreosService {

    /**
     * Envía hasta {@code notificaciones.envio.lote} correos, cada uno en su propia transacción.
     */
    ResumenEnvio procesar();
}
