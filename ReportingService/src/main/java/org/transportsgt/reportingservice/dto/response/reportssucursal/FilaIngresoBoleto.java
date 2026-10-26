package org.transportsgt.reportingservice.dto.response.reportssucursal;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Reporte 9: boletos vendidos de un viaje.
 */
public record FilaIngresoBoleto(
        UUID idViaje,
        String ruta,
        OffsetDateTime fechaHoraSalida,
        String placa,
        long boletosVendidos,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal ingreso
) {
}
