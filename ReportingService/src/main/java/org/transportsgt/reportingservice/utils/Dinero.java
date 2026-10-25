package org.transportsgt.reportingservice.utils;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Montos en GTQ con 2 decimales ({@code NUMERIC(12,2)}), redondeo {@link RoundingMode#HALF_UP}.
 */
@Component
public class Dinero {

    public static final int ESCALA = 2;
    public static final RoundingMode REDONDEO = RoundingMode.HALF_UP;

    /** {@code null} cuenta como cero: las sumas de los reportes nunca devuelven nulo. */
    public BigDecimal escalar(BigDecimal monto) {
        return (monto == null ? BigDecimal.ZERO : monto).setScale(ESCALA, REDONDEO);
    }

    /** Texto con 2 decimales ({@code "1234.50"}), como viaja el dinero en JSON. */
    public String texto(BigDecimal monto) {
        return escalar(monto).toPlainString();
    }

    public BigDecimal sumar(BigDecimal a, BigDecimal b) {
        return escalar(escalar(a).add(escalar(b)));
    }

    /** {@code a / b} con 2 decimales; cero si el divisor es cero o nulo. */
    public BigDecimal dividir(BigDecimal a, BigDecimal b) {
        if (b == null || b.signum() == 0) {
            return escalar(BigDecimal.ZERO);
        }
        return escalar(a).divide(b, ESCALA, REDONDEO);
    }
}
