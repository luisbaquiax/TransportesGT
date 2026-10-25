package org.transportsgt.reportingservice.repositories.reportsadmin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Fila del reporte de ingresos (proyección de la consulta nativa).
 */
public interface IngresoConsulta {
    UUID getIdSucursal();
    String getSucursal();
    LocalDate getFecha();
    String getTipo();
    UUID getReferencia();
    String getRutaOrigen();
    String getRutaDestino();
    String getOrigen();
    String getDestino();
    String getCliente();
    String getNit();
    Integer getCantidadBoletos();
    String getCanal();
    BigDecimal getMonto();
}
