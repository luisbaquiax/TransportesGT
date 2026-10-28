package org.transportsgt.ticketservice.clients.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.transportsgt.eventos.payload.costos.TipoReferencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /cartera/pagos} (catálogo §5). No lleva idUsuario: Cartera lo toma del JWT reenviado.
 */
public record PagoCarteraRequest(
        TipoReferencia tipoReferencia,
        UUID idReferencia,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal monto,
        LocalDate fechaPago,
        String descripcion
) {
}
