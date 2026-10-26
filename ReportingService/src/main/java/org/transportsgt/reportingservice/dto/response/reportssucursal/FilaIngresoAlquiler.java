package org.transportsgt.reportingservice.dto.response.reportssucursal;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Reporte 10: un alquiler pagado.
 *
 * @param placa bus asignado al viaje del alquiler; {@code null} si aún no tiene
 */
public record FilaIngresoAlquiler(
        UUID idAlquiler,
        String cliente,
        String nit,
        String origen,
        String destino,
        OffsetDateTime fechaSalida,
        OffsetDateTime fechaRetorno,
        String placa,
        LocalDate fechaPago,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal precioTotal
) {
}
