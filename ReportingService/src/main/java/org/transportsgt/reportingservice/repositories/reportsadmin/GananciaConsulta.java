package org.transportsgt.reportingservice.repositories.reportsadmin;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fila del reporte de ganancias (proyección de la consulta nativa).
 */
public interface GananciaConsulta {
    UUID getIdSucursal();
    String getSucursal();
    BigDecimal getIngresos();
    BigDecimal getGastos();
    BigDecimal getGanancia();
}
