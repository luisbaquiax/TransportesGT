package org.transportsgt.reportingservice.dto.response.reportsadmin;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Reporte 1: un ingreso (venta de boletos o alquiler).
 *
 * @param tipo        {@code VENTA_BOLETOS} o {@code ALQUILER}
 * @param referencia  idCompra o idAlquiler
 * @param descripcion ruta (origen → destino) o trayecto del alquiler
 */
public record FilaIngreso(
        UUID idSucursal,
        String sucursal,
        LocalDate fecha,
        String tipo,
        UUID referencia,
        String descripcion,
        String cliente,
        String nit,
        Integer cantidadBoletos,
        String canal,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal monto
) {
}
