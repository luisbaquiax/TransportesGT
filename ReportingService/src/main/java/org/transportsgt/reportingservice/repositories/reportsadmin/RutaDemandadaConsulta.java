package org.transportsgt.reportingservice.repositories.reportsadmin;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fila del reporte de rutas más demandadas (proyección de la consulta nativa).
 */
public interface RutaDemandadaConsulta {
    UUID getIdRuta();
    String getOrigen();
    String getDestino();
    BigDecimal getDistanciaKm();
    BigDecimal getPrecioBoleto();
    Boolean getEliminada();
    Long getBoletosVendidos();
    Long getViajesConVentas();
    BigDecimal getIngreso();
}
