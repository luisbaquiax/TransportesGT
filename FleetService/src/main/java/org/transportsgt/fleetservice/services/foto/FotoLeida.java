package org.transportsgt.fleetservice.services.foto;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

/**
 * Contenido de una foto y su tipo, listos para responder.
 */
public record FotoLeida(Resource contenido, MediaType tipo) {
}
