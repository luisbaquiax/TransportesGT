package org.transportsgt.reportingservice.dto.response.reportsadmin;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Reportes 5 y 12: una línea de taller.
 *
 * @param tipoLinea {@code MANO_DE_OBRA} o {@code REPUESTO}
 */
public record FilaGastoTaller(
        String categoria,
        UUID idBus,
        String placa,
        String sucursal,
        LocalDate fecha,
        String descripcion,
        String tipoLinea,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal monto
) {
}
