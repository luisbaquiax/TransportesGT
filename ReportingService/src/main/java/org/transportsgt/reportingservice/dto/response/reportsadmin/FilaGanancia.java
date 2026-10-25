package org.transportsgt.reportingservice.dto.response.reportsadmin;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Reporte 3: ingresos, gastos y ganancia neta de una sucursal.
 */
public record FilaGanancia(
        UUID idSucursal,
        String sucursal,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal ingresos,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal gastos,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal ganancia
) {
}
