package org.transportsgt.fleetservice.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Fotos de buses y choferes (plan §5, decisión 1). Los valores por defecto viven solo en
 * {@code application.properties}; si falta uno obligatorio el servicio no arranca.
 *
 * <p>Desarrollo (por defecto): se guardan en disco ({@code FOTOS_DIRECTORIO}). Producción: se suben a
 * Cloud Storage con {@code FOTOS_NUBE_HABILITADA=true} y {@code FOTOS_NUBE_BUCKET}; solo cambian variables
 * de entorno. En ambos casos son privadas y se sirven por GET /buses/{id}/foto y GET /choferes/{id}/foto.
 *
 * @param directorio         carpeta local de las fotos (solo almacenamiento local)
 * @param tamanioMaximoBytes tamaño máximo de una imagen
 * @param nube               {@code flota.fotos.nube.*}
 */
@Validated
@ConfigurationProperties(prefix = "flota.fotos")
public record FotosProperties(@NotBlank String directorio, @Positive long tamanioMaximoBytes,
                              @Valid @NotNull Nube nube) {

    /**
     * @param habilitada usa Cloud Storage en vez del disco
     * @param bucket     obligatorio si {@code habilitada} (lo valida {@code AlmacenamientoFotosNube})
     */
    public record Nube(boolean habilitada, String bucket) {
    }
}
