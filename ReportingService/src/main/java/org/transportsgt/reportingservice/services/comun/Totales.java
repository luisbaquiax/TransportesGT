package org.transportsgt.reportingservice.services.comun;

import org.transportsgt.reportingservice.utils.Dinero;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Arma los subtotales y totales de un reporte en orden; el dinero queda como texto con 2 decimales.
 * Se obtiene con {@link ArmadorReporteTabla#totales()}.
 */
public class Totales {

    private final Dinero dinero;
    private final Map<String, Object> valores = new LinkedHashMap<>();

    Totales(Dinero dinero) {
        this.dinero = dinero;
    }

    public Totales dinero(String clave, BigDecimal monto) {
        valores.put(clave, dinero.texto(monto));
        return this;
    }

    public Totales cantidad(String clave, long cantidad) {
        valores.put(clave, cantidad);
        return this;
    }

    public Totales decimal(String clave, BigDecimal valor) {
        valores.put(clave, valor == null ? "0" : valor.stripTrailingZeros().toPlainString());
        return this;
    }

    public Map<String, Object> mapa() {
        return valores;
    }
}
