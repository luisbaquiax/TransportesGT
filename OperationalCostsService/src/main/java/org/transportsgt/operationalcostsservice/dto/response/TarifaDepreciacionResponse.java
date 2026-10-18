package org.transportsgt.operationalcostsservice.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TarifaDepreciacionResponse(
        UUID id,
        BigDecimal montoPorKm,
        LocalDate vigenteDesde,
        UUID creadoPor,
        OffsetDateTime creadoEn
) {
}
