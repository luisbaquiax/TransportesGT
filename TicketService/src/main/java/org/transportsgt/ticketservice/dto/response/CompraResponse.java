package org.transportsgt.ticketservice.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.transportsgt.ticketservice.models.EstadoCompra;
import org.transportsgt.eventos.payload.boletos.Canal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Una compra con sus boletos.
 */
public record CompraResponse(
        UUID idCompra,
        UUID idViaje,
        Canal canal,
        EstadoCompra estado,
        List<BoletoResponse> asientos,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal precioUnitario,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal montoTotal,
        String nitComprador,
        String nombreComprador,
        UUID idPago,
        LocalDate fechaPago,
        OffsetDateTime reservaExpiraEn,
        String motivoAnulacion,
        OffsetDateTime creadoEn,
        OffsetDateTime confirmadoEn
) {
}
