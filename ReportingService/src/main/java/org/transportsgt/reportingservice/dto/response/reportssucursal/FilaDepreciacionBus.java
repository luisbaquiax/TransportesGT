package org.transportsgt.reportingservice.dto.response.reportssucursal;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Reporte 11: depreciación de un bus.
 *
 * @param montoPorKmActual tarifa vigente hoy (la misma para todos los buses)
 * @param promedioPorKm    depreciación acumulada / km recorridos (tarifas aplicadas en cada viaje)
 */
public record FilaDepreciacionBus(
        UUID idBus,
        String placa,
        BigDecimal kmRecorridos,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal montoPorKmActual,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal depreciacionAcumulada,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal promedioPorKm
) {
}
