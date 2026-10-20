package org.transportsgt.notificationservice.utils;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FechasTest {

    @Test
    void hoyYAhora_cuandoEnUtcYaEsOtroDia_usanLaHoraDeGuatemala() {
        // Arrange: 05:30 UTC del 21 = 23:30 del 20 en Guatemala
        Fechas fechas = new Fechas(Clock.fixed(Instant.parse("2026-10-21T05:30:00Z"), ZoneOffset.UTC));

        // Act & Assert
        assertAll(
                () -> assertEquals(LocalDate.parse("2026-10-20"), fechas.hoy()),
                () -> assertEquals(OffsetDateTime.parse("2026-10-20T23:30-06:00"), fechas.ahora()),
                () -> assertEquals(OffsetDateTime.parse("2026-10-20T00:00-06:00"),
                        fechas.inicioDelDia(LocalDate.parse("2026-10-20")))
        );
    }
}
