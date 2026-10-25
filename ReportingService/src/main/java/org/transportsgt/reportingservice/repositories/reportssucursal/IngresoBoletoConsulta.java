package org.transportsgt.reportingservice.repositories.reportssucursal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Ventas de boletos de un viaje (proyección de la consulta nativa).
 */
public interface IngresoBoletoConsulta {
    UUID getIdViaje();
    String getOrigen();
    String getDestino();
    Instant getFechaHoraSalida();
    String getPlaca();
    Long getBoletosVendidos();
    BigDecimal getIngreso();
}
