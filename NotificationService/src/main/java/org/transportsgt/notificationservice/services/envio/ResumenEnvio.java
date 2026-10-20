package org.transportsgt.notificationservice.services.envio;

/**
 * Resultado de una corrida del envío.
 *
 * @param reprogramadas fallaron y se reintentarán más tarde
 * @param fallidas      agotaron los intentos y quedaron FALLIDA
 */
public record ResumenEnvio(int enviadas, int reprogramadas, int fallidas) {
}
