package org.transportsgt.reportingservice.repositories.catalogos;

import java.util.UUID;

/**
 * Opción de catálogo (proyección de la consulta nativa).
 */
public interface OpcionConsulta {
    UUID getId();
    String getNombre();
    Boolean getActivo();
}
