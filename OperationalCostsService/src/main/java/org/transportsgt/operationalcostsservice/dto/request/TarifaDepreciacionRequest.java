package org.transportsgt.operationalcostsservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TarifaDepreciacionRequest(
        @NotNull @DecimalMin(value = "0", message = "debe ser mayor o igual a 0")
        BigDecimal montoPorKm,

        @NotNull @PastOrPresent(message = "no puede ser futura")
        LocalDate vigenteDesde
) {
}
