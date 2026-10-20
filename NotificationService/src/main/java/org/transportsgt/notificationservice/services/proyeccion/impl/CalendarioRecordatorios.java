package org.transportsgt.notificationservice.services.proyeccion.impl;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

/**
 * Fechas de los avisos de licencia: 30, 15 y 3 días antes del vencimiento. Se omiten los que caen antes de hoy;
 * el de hoy sí se programa (lo envía el job de hoy).
 */
@Component
class CalendarioRecordatorios {

    static final List<Short> ANTICIPACIONES = List.of((short) 30, (short) 15, (short) 3);

    record Aviso(short diasAnticipacion, LocalDate fechaEnvio) {
    }

    List<Aviso> avisos(LocalDate fechaVencimiento, LocalDate hoy) {
        return ANTICIPACIONES.stream()
                .flatMap(dias -> {
                    LocalDate envio = fechaVencimiento.minusDays(dias);
                    return envio.isBefore(hoy) ? Stream.empty() : Stream.of(new Aviso(dias, envio));
                })
                .toList();
    }
}
