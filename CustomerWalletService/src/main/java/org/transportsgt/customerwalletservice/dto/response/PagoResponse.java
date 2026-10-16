package org.transportsgt.customerwalletservice.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Respuesta de {@code POST /cartera/pagos} (contrato del catálogo §5).
 */
public record PagoResponse(
        UUID idPago,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal saldoPosterior
) {
}
