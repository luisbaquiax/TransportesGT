package org.transportsgt.customerwalletservice.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.transportsgt.customerwalletservice.models.TipoMovimiento;
import org.transportsgt.eventos.payload.TipoReferencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Una línea del libro de movimientos (recarga, pago o reembolso).
 */
public record MovimientoResponse(
        UUID idMovimiento,
        TipoMovimiento tipo,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal monto,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal saldoPosterior,
        TipoReferencia tipoReferencia,
        UUID idReferencia,
        LocalDate fechaMovimiento,
        String descripcion,
        OffsetDateTime creadoEn
) {
}
