package org.transportsgt.privaterentalservice.services.solicitud.impl;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculadoraDistanciaTest {

    private final CalculadoraDistancia calculadora = new CalculadoraDistancia(1.3);

    @Test
    void xelaCapitalRondaLosEsperados() {
        // Quetzaltenango ~14.8347,-91.5181 → Ciudad de Guatemala ~14.6427,-90.5133
        BigDecimal km = calculadora.distanciaCarretera(
                new BigDecimal("14.8347"), new BigDecimal("-91.5181"),
                new BigDecimal("14.6427"), new BigDecimal("-90.5133"));
        assertTrue(km.doubleValue() > 130 && km.doubleValue() < 160,
                "Xela→Capital por carretera ronda ~143 km, salió " + km);
    }

    @Test
    void mismoPuntoDaCero() {
        BigDecimal km = calculadora.distanciaCarretera(
                new BigDecimal("14.8347"), new BigDecimal("-91.5181"),
                new BigDecimal("14.8347"), new BigDecimal("-91.5181"));
        assertEquals(0, km.compareTo(BigDecimal.ZERO));
    }

    @Test
    void factorUnoEsDistanciaRecta() {
        CalculadoraDistancia recta = new CalculadoraDistancia(1.0);
        BigDecimal km = recta.distanciaCarretera(
                new BigDecimal("14.8347"), new BigDecimal("-91.5181"),
                new BigDecimal("14.6427"), new BigDecimal("-90.5133"));
        assertTrue(km.doubleValue() > 100 && km.doubleValue() < 120,
                "Recta Xela→Capital ronda ~110 km, salió " + km);
    }
}
