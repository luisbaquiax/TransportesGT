package org.transportsgt.fleetservice.services;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.UUID;

/**
 * Fotos de buses y choferes. Son privadas: se sirven por GET /buses/{id}/foto y GET /choferes/{id}/foto con
 * la misma autorización por sucursal que el registro (GET /fotos/** queda restringido y pendiente de retirar).
 * Dónde se guardan lo decide la variable {@code FOTOS_NUBE_HABILITADA}: disco local en
 * desarrollo (por ejemplo {@code ./public/fotos}) o Cloud Storage en producción (plan §5).
 */
public interface FotoService {

    enum Tipo {
        BUSES, CHOFERES
    }

    /** Contenido de una foto y su tipo, listos para responder. */
    record FotoLeida(Resource contenido, MediaType tipo) {
    }

    /**
     * Valida y guarda la imagen; devuelve la referencia interna que se guarda en foto_url (nunca se expone).
     */
    String guardar(Tipo tipo, UUID id, MultipartFile archivo);

    /**
     * Lee una foto guardada; vacío si no existe.
     */
    Optional<Resource> leer(Tipo tipo, String nombre);

    /**
     * Lee la foto a partir de lo guardado en foto_url (clave o URL de versiones anteriores); vacío si no existe.
     */
    Optional<FotoLeida> leerGuardada(Tipo tipo, String fotoGuardada);

    /**
     * Borra la foto anterior al reemplazarla (si existía).
     */
    void borrar(Tipo tipo, UUID id);
}
