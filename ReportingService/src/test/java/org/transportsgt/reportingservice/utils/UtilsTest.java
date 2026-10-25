package org.transportsgt.reportingservice.utils;

import org.junit.jupiter.api.Test;
import org.transportsgt.reportingservice.support.Soporte;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UtilsTest {

    @Test
    void dinero_escalaSumaDivideYFormatea() {
        Dinero dinero = new Dinero();
        assertAll(
                () -> assertEquals(new BigDecimal("0.00"), dinero.escalar(null)),
                () -> assertEquals(new BigDecimal("10.01"), dinero.escalar(new BigDecimal("10.005"))),
                () -> assertEquals("1234.50", dinero.texto(new BigDecimal("1234.5"))),
                () -> assertEquals(new BigDecimal("3.00"), dinero.sumar(new BigDecimal("1.5"), new BigDecimal("1.5"))),
                () -> assertEquals(new BigDecimal("2.00"), dinero.dividir(new BigDecimal("411"), new BigDecimal("205.5"))),
                () -> assertEquals(new BigDecimal("0.00"), dinero.dividir(BigDecimal.TEN, BigDecimal.ZERO)),
                () -> assertEquals(new BigDecimal("0.00"), dinero.dividir(BigDecimal.TEN, null))
        );
    }

    @Test
    void fechas_usanElRelojEnHoraDeGuatemala() {
        Fechas fechas = Soporte.fechas();
        assertAll(
                () -> assertEquals(Soporte.HOY, fechas.hoy()),
                () -> assertEquals("2026-10-24T12:00-06:00", fechas.ahora().toString()),
                () -> assertEquals("2026-10-21T23:30-06:00",
                        fechas.enGuatemala(Instant.parse("2026-10-22T05:30:00Z")).toString()),
                () -> assertNull(fechas.enGuatemala(null))
        );
    }
}
