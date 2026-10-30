package org.transportsgt.reportingservice.services.exportacion.reports;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class FilaReportePdf {

    private final String grupo1;
    private final String grupo2;
    private final String c1;
    private final String c2;
    private final String c3;
    private final String c4;
    private final String c5;
    private final String c6;
    private final String c7;
    private final String c8;
    private final BigDecimal numero1;
    private final BigDecimal numero2;

    public FilaReportePdf(String grupo1, String grupo2, String[] columnas,
                          BigDecimal numero1, BigDecimal numero2) {
        this.grupo1 = grupo1;
        this.grupo2 = grupo2;
        this.c1 = columna(columnas, 0);
        this.c2 = columna(columnas, 1);
        this.c3 = columna(columnas, 2);
        this.c4 = columna(columnas, 3);
        this.c5 = columna(columnas, 4);
        this.c6 = columna(columnas, 5);
        this.c7 = columna(columnas, 6);
        this.c8 = columna(columnas, 7);
        this.numero1 = numero1 == null ? BigDecimal.ZERO : numero1;
        this.numero2 = numero2 == null ? BigDecimal.ZERO : numero2;
    }

    private String columna(String[] columnas, int indice) {
        return indice < columnas.length && columnas[indice] != null ? columnas[indice] : "—";
    }
}
