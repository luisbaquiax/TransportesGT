package org.transportsgt.reportingservice.repositories.reportssucursal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

/**
 * Un alquiler pagado (proyección de la consulta nativa).
 */
public interface IngresoAlquilerConsulta {
    UUID getIdAlquiler();
    String getCliente();
    String getNit();
    String getOrigen();
    String getDestino();
    Instant getFechaSalida();
    Instant getFechaRetorno();
    String getPlaca();
    LocalDate getFechaPago();
    BigDecimal getPrecioTotal();
}
