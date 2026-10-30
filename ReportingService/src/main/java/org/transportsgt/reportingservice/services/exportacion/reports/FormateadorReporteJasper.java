package org.transportsgt.reportingservice.services.exportacion.reports;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class FormateadorReporteJasper {
    public BigDecimal decimal(Number valor) {
        return valor == null ? BigDecimal.ZERO : new BigDecimal(valor.toString());
    }

    public String moneda(BigDecimal valor) {
        return valor == null ? "Q 0.00" : "Q " + valor.setScale(2).toPlainString();
    }

    public String texto(Object valor) {
        return valor == null ? "—" : String.valueOf(valor);
    }
}
