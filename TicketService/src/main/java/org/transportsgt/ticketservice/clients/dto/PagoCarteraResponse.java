package org.transportsgt.ticketservice.clients.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Respuesta de {@code POST /cartera/pagos}: 201 (nuevo) o 200 (ya existía).
 */
public record PagoCarteraResponse(
        UUID idPago,
        BigDecimal saldoPosterior) {
}
