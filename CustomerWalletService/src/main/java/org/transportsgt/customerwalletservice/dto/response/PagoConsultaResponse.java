package org.transportsgt.customerwalletservice.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Respuesta de {@code GET /cartera/pagos} (contrato del catálogo §5).
 */
public record PagoConsultaResponse(
        UUID idPago,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal monto,
        LocalDate fechaPago
) {
}
