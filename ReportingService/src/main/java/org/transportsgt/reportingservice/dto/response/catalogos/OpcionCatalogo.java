package org.transportsgt.reportingservice.dto.response.catalogos;

import java.util.UUID;

/**
 * Opción para llenar un filtro del frontend.
 *
 * @param activo {@code false} para rutas eliminadas o buses inactivos (siguen sirviendo para reportes históricos)
 */
public record OpcionCatalogo(
        UUID id,
        String nombre,
        boolean activo
) {
}
