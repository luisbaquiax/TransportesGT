package org.transportsgt.privaterentalservice.services.solicitud.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Distancia en km entre dos puntos (Haversine) multiplicada por el factor de carretera
 * ({@code alquileres.factor-carretera}, por defecto 1.3).
 */
@Component
class CalculadoraDistancia {

    private static final double RADIO_TIERRA_KM = 6371.0;

    private final double factorCarretera;

    CalculadoraDistancia(@Value("${alquileres.factor-carretera:1.3}") double factorCarretera) {
        this.factorCarretera = factorCarretera;
    }

    BigDecimal distanciaCarretera(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        double dLat = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double dLon = Math.toRadians(lon2.doubleValue() - lon1.doubleValue());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1.doubleValue())) * Math.cos(Math.toRadians(lat2.doubleValue()))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double km = RADIO_TIERRA_KM * c * factorCarretera;
        return BigDecimal.valueOf(km).setScale(2, RoundingMode.HALF_UP);
    }
}
