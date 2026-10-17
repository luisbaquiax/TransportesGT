package org.transportsgt.customerwalletservice.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CarteraResponse(
        UUID idCartera,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal saldo,
        OffsetDateTime actualizadoEn
) {
}
