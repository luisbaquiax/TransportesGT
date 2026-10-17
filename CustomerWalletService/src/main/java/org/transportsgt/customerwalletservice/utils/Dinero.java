package org.transportsgt.customerwalletservice.utils;

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

    public BigDecimal escalar(BigDecimal monto) {
        return monto == null ? null : monto.setScale(ESCALA, REDONDEO);
    }

    /**
     * Compara montos sin importar la escala ({@code 10.0} = {@code 10.00}).
     */
    public boolean iguales(BigDecimal a, BigDecimal b) {
        return a != null && b != null && a.compareTo(b) == 0;
    }
}
