package org.transportsgt.reportingservice.repositories.reportsadmin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Fila del reporte de gastos (proyección de la consulta nativa).
 */
public interface GastoConsulta {
    UUID getIdSucursal();
    String getSucursal();
    LocalDate getFecha();
    String getTipoGasto();
    String getTipoLinea();
    UUID getIdReferencia();
    String getPlaca();
    String getCategoria();
    String getDescripcion();
    String getChofer();
    BigDecimal getDistanciaKm();
    BigDecimal getMontoPorKm();
    BigDecimal getMonto();
}
