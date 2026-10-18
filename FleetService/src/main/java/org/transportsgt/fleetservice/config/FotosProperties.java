package org.transportsgt.fleetservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Fotos de buses y choferes (plan §5, decisión 1).
 *
 * <p>Desarrollo (por defecto): se guardan en disco ({@code FOTOS_DIRECTORIO}, por ejemplo
 * {@code ./public/fotos}) y se sirven por {@code GET /fotos/**}. Producción: se suben a
 * Cloud Storage cuando {@code FOTOS_NUBE_HABILITADA=true} y {@code FOTOS_NUBE_BUCKET} está
 * definido; solo cambian variables de entorno.
 */
@ConfigurationProperties(prefix = "flota.fotos")
public record FotosProperties(String directorio, String urlPublica, long tamanioMaximoBytes,
                              boolean nubeHabilitada, String nubeBucket) {

    public FotosProperties {
        if (directorio == null || directorio.isBlank()) {
            directorio = "./public/fotos";
        }
        if (urlPublica == null || urlPublica.isBlank()) {
            urlPublica = "http://localhost:8082/v1/fleet/fotos";
        }
        if (tamanioMaximoBytes <= 0) {
            tamanioMaximoBytes = 5L * 1024 * 1024;
        }
        if (nubeBucket == null) {
            nubeBucket = "";
        }
    }
}
