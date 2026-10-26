package org.transportsgt.customerwalletservice.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UtilsTest {

    private Dinero dinero;
    private Fechas fechas;

    @BeforeEach
    void setUp() {
        dinero = new Dinero();
        fechas = new Fechas();
    }

    @Nested
    class Escalar {

        @Test
        void escalar_redondeaAMitadHaciaArribaConDosDecimales() {
            assertAll(
                    () -> assertEquals(new BigDecimal("10.13"), dinero.escalar(new BigDecimal("10.125"))),
                    () -> assertEquals(new BigDecimal("10.12"), dinero.escalar(new BigDecimal("10.1249"))),
                    () -> assertEquals(new BigDecimal("7.00"), dinero.escalar(new BigDecimal("7"))),
                    () -> assertNull(dinero.escalar(null))
            );
        }
    }

    @Nested
    class Iguales {

        @Test
        void iguales_comparaSinImportarLaEscala() {
            assertAll(
                    () -> assertTrue(dinero.iguales(new BigDecimal("10.0"), new BigDecimal("10.00"))),
                    () -> assertFalse(dinero.iguales(new BigDecimal("10.01"), new BigDecimal("10.00"))),
                    () -> assertFalse(dinero.iguales(null, BigDecimal.ONE)),
                    () -> assertFalse(dinero.iguales(BigDecimal.ONE, null))
            );
        }
    }

    @Nested
    class EnGuatemala {

        @Test
        void enGuatemala_cuandoEnUtcYaEsOtroDia_devuelveLaFechaLocal() {
            assertAll(
                    () -> assertEquals(LocalDate.parse("2026-10-21"),
                            fechas.enGuatemala(Instant.parse("2026-10-22T05:59:59Z"))),
                    () -> assertEquals(LocalDate.parse("2026-10-22"),
                            fechas.enGuatemala(Instant.parse("2026-10-22T06:00:00Z")))
            );
        }
    }
}
