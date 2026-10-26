package org.transportsgt.privaterentalservice.services.comun;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
public class CalendarioGuatemala {

    private static final ZoneId ZONA = ZoneId.of("America/Guatemala");

    private final Clock clock;

    public OffsetDateTime ahora() {
        return OffsetDateTime.now(clock);
    }

    public LocalDate hoy() {
        return LocalDate.now(clock.withZone(ZONA));
    }

    /**
     * Día de Guatemala en que cae un instante.
     */
    public LocalDate dia(OffsetDateTime instante) {
        return instante.atZoneSameInstant(ZONA).toLocalDate();
    }

    /**
     * 00:00 de Guatemala del día dado.
     */
    public OffsetDateTime inicioDia(LocalDate dia) {
        return dia.atStartOfDay(ZONA).toOffsetDateTime();
    }
}
