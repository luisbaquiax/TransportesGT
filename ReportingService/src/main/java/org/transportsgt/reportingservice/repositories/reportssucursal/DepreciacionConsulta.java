package org.transportsgt.reportingservice.repositories.reportssucursal;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Depreciación acumulada de un bus (proyección de la consulta nativa).
 */
public interface DepreciacionConsulta {
    UUID getIdBus();
    String getPlaca();
    BigDecimal getKmRecorridos();
    BigDecimal getDepreciacionAcumulada();
}
