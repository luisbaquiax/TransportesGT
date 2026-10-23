package org.transportsgt.privaterentalservice.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Resultado del cobro: {@code idPago} es el movimiento de Cartera (null en efectivo).
 */
public record PagoRealizado(UUID idPago, LocalDate fechaPago) {
}
