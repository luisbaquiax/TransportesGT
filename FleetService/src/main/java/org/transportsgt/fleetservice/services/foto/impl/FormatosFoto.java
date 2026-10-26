package org.transportsgt.fleetservice.services.foto.impl;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Formatos aceptados: content-type ↔ extensión del archivo ↔ tipo con que se sirve.
 */
@Component
class FormatosFoto {

    private static final Map<String, String> EXTENSIONES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    /**
     * Extensión para un content-type permitido; vacío si el formato no se acepta.
     */
    Optional<String> extensionDe(String contentType) {
        return Optional.ofNullable(EXTENSIONES.get(normalizar(contentType)));
    }

    String normalizar(String contentType) {
        return contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
    }

    /**
     * Tipo con que se sirve un archivo guardado, según su extensión (JPEG si no se reconoce).
     */
    MediaType tipoDe(String nombre) {
        String extension = nombre.substring(nombre.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "png" -> MediaType.IMAGE_PNG;
            case "webp" -> MediaType.parseMediaType("image/webp");
            default -> MediaType.IMAGE_JPEG;
        };
    }
}
