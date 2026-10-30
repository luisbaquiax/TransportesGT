package org.transportsgt.reportingservice.services.exportacion.reports;

import java.math.BigDecimal;

public record DatosFilaJasper(String[] columnas, BigDecimal numero1, BigDecimal numero2) {
    public DatosFilaJasper(BigDecimal numero1, BigDecimal numero2, String... columnas) {
        this(columnas, numero1, numero2);
    }
}
