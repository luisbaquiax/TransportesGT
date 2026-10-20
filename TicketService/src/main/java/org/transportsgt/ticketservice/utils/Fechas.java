package org.transportsgt.ticketservice.utils;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Fechas de negocio en hora de Guatemala (UTC-6, sin horario de verano).
 */
@Component
public class Fechas {

    public static final ZoneId ZONA_GUATEMALA = ZoneId.of("America/Guatemala");

    public OffsetDateTime inicioDelDia(LocalDate fecha) {
        return fecha.atStartOfDay(ZONA_GUATEMALA).toOffsetDateTime();
    }

    public OffsetDateTime ahora(Instant instante) {
        return instante.atZone(ZONA_GUATEMALA).toOffsetDateTime();
    }
}
