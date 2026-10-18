package org.transportsgt.fleetservice.almacenamiento;

import org.springframework.core.io.Resource;

import java.util.Optional;

/**
 * Dónde viven las fotos de buses y choferes. La implementación activa la decide la
 * variable de entorno {@code FOTOS_NUBE_HABILITADA} (plan §5, decisión 1):
 *
 * <ul>
 *   <li>{@code false} (por defecto, desarrollo): disco local bajo {@code FOTOS_DIRECTORIO},
 *       servidas por {@code GET /fotos/**}.</li>
 *   <li>{@code true} (producción): bucket de Google Cloud Storage ({@code FOTOS_NUBE_BUCKET} +
 *       credenciales por {@code GOOGLE_APPLICATION_CREDENTIALS} o identidad de la plataforma).</li>
 * </ul>
 *
 * Todas las operaciones trabajan con <em>claves</em> de la forma {@code buses/{uuid}.jpg},
 * sin barra inicial: {@code FotoService} valida el nombre antes de llamar.
 */
public interface AlmacenamientoFotos {

    /**
     * Guarda (o reemplaza) el objeto y devuelve su URL pública, que es la que se
     * guarda en {@code foto_url}.
     */
    String guardar(String clave, byte[] contenido, String contentType);

    /** Lee un objeto; vacío si no existe. */
    Optional<Resource> leer(String clave);

    /** Borra un objeto exacto; no falla si no existía. */
    void borrar(String clave);

    /**
     * Borra todos los objetos cuya clave empieza con el prefijo. Se usa al reemplazar una
     * foto para eliminar también las extensiones anteriores ({@code buses/{uuid}.png} → …jpg).
     */
    void borrarPorPrefijo(String prefijo);

    /** URL pública del objeto (la que verá el frontend en {@code <img src>}). */
    String urlPublica(String clave);
}
