package org.transportsgt.reportingservice.repositories.reportssucursal;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Fila del listado de choferes (proyección de la consulta nativa).
 */
public interface ChoferConsulta {
    UUID getIdChofer();
    String getNumeroLicencia();
    String getNombre();
    String getTipoLicencia();
    LocalDate getFechaVencimiento();
    Boolean getActivo();
    Long getViajesRealizados();
}
