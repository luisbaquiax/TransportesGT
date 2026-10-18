package org.transportsgt.fleetservice.services;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.UUID;

/**
 * Fotos de buses y choferes. Se sirven por GET /fotos/{tipo}/{nombre} con lectura pública.
 * Dónde se guardan lo decide la variable {@code FOTOS_NUBE_HABILITADA}: disco local en
 * desarrollo (por ejemplo {@code ./public/fotos}) o Cloud Storage en producción (plan §5).
 */
public interface FotoService {

    enum Tipo {
        BUSES, CHOFERES
    }

    /**
     * Valida y guarda la imagen; devuelve la URL pública (foto_url).
     */
    String guardar(Tipo tipo, UUID id, MultipartFile archivo);

    /**
     * Lee una foto guardada; vacío si no existe.
     */
    Optional<Resource> leer(Tipo tipo, String nombre);

    /**
     * Borra la foto anterior al reemplazarla (si existía).
     */
    void borrar(Tipo tipo, UUID id);
}
