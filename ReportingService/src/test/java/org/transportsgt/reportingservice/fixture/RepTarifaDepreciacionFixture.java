package org.transportsgt.reportingservice.fixture;

import org.transportsgt.reportingservice.models.RepTarifaDepreciacion;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class RepTarifaDepreciacionFixture {

    private RepTarifaDepreciacionFixture() {
    }

    public static RepTarifaDepreciacion tarifa(String montoPorKm, LocalDate vigenteDesde) {
        return RepTarifaDepreciacion.builder().montoPorKm(new BigDecimal(montoPorKm)).vigenteDesde(vigenteDesde).build();
    }
}
