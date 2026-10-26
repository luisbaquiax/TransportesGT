package org.transportsgt.reportingservice.repositories.reportsadmin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Línea de taller (reportes 5 y 12).
 */
public interface GastoTallerConsulta {
    String getCategoria();
    UUID getIdBus();
    String getPlaca();
    String getSucursal();
    LocalDate getFecha();
    String getDescripcion();
    String getTipoLinea();
    BigDecimal getMonto();
}
