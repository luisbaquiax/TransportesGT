package org.transportsgt.notificationservice.services.licencia;

/**
 * Resultado de una corrida de los avisos de licencia.
 *
 * @param enviados  avisos con sus correos encolados (al chofer y a los administradores)
 * @param cancelados el chofer se desactivó o cambió su fecha de vencimiento
 * @param conError  fallaron y siguen PENDIENTE para la próxima corrida
 */
public record ResumenAvisos(int enviados, int cancelados, int conError) {
}
