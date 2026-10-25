package org.transportsgt.reportingservice.repositories.reportssucursal;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fila del listado de buses (proyección de la consulta nativa).
 */
public interface BusConsulta {
    UUID getIdBus();
    String getPlaca();
    String getMarca();
    String getModelo();
    Integer getCapacidad();
    String getEstadoOperativo();
    String getChofer();
    BigDecimal getKilometrajeActual();
    Long getViajesRealizados();
    Boolean getActivo();
}
