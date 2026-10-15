package org.transportsgt.customerwalletservice.utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Fechas de negocio en hora de Guatemala (UTC-6, sin horario de verano).
 */
public final class Fechas {

    public static final ZoneId ZONA_GUATEMALA = ZoneId.of("America/Guatemala");

    private Fechas() {
    }

    /**
     * Fecha de un reembolso: la del evento ({@code canceladoEn} u {@code ocurridoEn}) en hora de Guatemala.
     */
    public static LocalDate enGuatemala(Instant instante) {
        return LocalDate.ofInstant(instante, ZONA_GUATEMALA);
    }
}
