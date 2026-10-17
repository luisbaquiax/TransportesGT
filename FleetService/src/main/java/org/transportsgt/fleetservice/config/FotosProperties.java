package org.transportsgt.fleetservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Fotos de buses y choferes: se guardan en disco y se sirven por GET /fotos/** (decisión de equipo:
 * alternativa al bucket de Cloud Storage, plan §5).
 */
@ConfigurationProperties(prefix = "flota.fotos")
public record FotosProperties(String directorio, String urlPublica, long tamanioMaximoBytes) {

    public FotosProperties {
        if (directorio == null || directorio.isBlank()) {
            directorio = "./fotos";
        }
        if (urlPublica == null || urlPublica.isBlank()) {
            urlPublica = "http://localhost:8082/v1/fleet/fotos";
        }
        if (tamanioMaximoBytes <= 0) {
            tamanioMaximoBytes = 5L * 1024 * 1024;
        }
    }
}
