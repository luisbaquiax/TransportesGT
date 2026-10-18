package org.transportsgt.privaterentalservice.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.transportsgt.privaterentalservice.models.TipoCotizacion;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CotizacionAlquilerResponse(
        UUID id,
        TipoCotizacion tipo,
        BigDecimal precio,
        UUID idUsuario,
        String comentario,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX") OffsetDateTime creadoEn
) {
}
