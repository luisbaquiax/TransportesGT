package org.transportsgt.reportingservice.dto.response.reportsadmin;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Reporte 2: un gasto.
 *
 * @param tipo {@code COMBUSTIBLE}, {@code TALLER} (mano de obra), {@code REPUESTO}, {@code DEPRECIACION} o
 *             {@code SALARIO_CHOFER}
 */
public record FilaGasto(
        UUID idSucursal,
        String sucursal,
        LocalDate fecha,
        String tipo,
        UUID referencia,
        String placa,
        String descripcion,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal monto
) {
}
