package org.transportsgt.reportingservice.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Fechas de negocio en hora de Guatemala (UTC-6, sin horario de verano). "Hoy" sale del {@link Clock} inyectado.
 */
@Component
@RequiredArgsConstructor
public class Fechas {

    public static final ZoneId ZONA_GUATEMALA = ZoneId.of("America/Guatemala");

    private final Clock clock;

    /** Fecha de hoy en Guatemala (tarifa vigente, licencias por vencer, próximo viaje). */
    public LocalDate hoy() {
        return LocalDate.now(clock.withZone(ZONA_GUATEMALA));
    }

    /** Fecha y hora actual en Guatemala ({@code generadoEn} de los reportes). */
    public OffsetDateTime ahora() {
        return OffsetDateTime.now(clock.withZone(ZONA_GUATEMALA));
    }

    /** Un instante del evento (p. ej. {@code canceladoEn}) con la zona de Guatemala. */
    public OffsetDateTime enGuatemala(Instant instante) {
        return instante == null ? null : instante.atZone(ZONA_GUATEMALA).toOffsetDateTime();
    }
}
