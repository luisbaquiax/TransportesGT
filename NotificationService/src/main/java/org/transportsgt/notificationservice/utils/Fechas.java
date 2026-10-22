package org.transportsgt.notificationservice.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
public class Fechas {

    public static final ZoneId ZONA_GUATEMALA = ZoneId.of("America/Guatemala");

    private final Clock clock;

    /**
     * Hoy en Guatemala: decide qué avisos de licencia ya pasaron y cuáles toca enviar.
     */
    public LocalDate hoy() {
        return LocalDate.now(clock.withZone(ZONA_GUATEMALA));
    }

    public OffsetDateTime ahora() {
        return OffsetDateTime.now(clock.withZone(ZONA_GUATEMALA));
    }

    /**
     * 00:00 del día en Guatemala: para filtrar la bitácora por fecha.
     */
    public OffsetDateTime inicioDelDia(LocalDate fecha) {
        return fecha.atStartOfDay(ZONA_GUATEMALA).toOffsetDateTime();
    }
}
