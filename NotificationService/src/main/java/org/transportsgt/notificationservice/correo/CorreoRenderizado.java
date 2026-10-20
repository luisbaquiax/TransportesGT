package org.transportsgt.notificationservice.correo;

/**
 * Resultado de una plantilla: el asunto (su {@code <title>}) y el HTML completo.
 */
public record CorreoRenderizado(String asunto, String html) {
}
