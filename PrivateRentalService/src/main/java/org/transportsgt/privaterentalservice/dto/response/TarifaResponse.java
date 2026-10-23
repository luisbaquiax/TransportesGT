package org.transportsgt.privaterentalservice.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TarifaResponse(
        UUID id,
        BigDecimal precioPorKm,
        BigDecimal precioPorDia,
        LocalDate vigenteDesde,
        UUID creadoPor,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX") OffsetDateTime creadoEn
) {
}
