package org.transportsgt.ticketservice.clients.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Respuesta de {@code GET /cartera/pagos?tipoReferencia=&idReferencia=} (200; 404 si no hubo pago).
 */
public record PagoCarteraConsulta(
        UUID idPago,
        BigDecimal monto,
        LocalDate fechaPago
) {
}
