package org.transportsgt.tripservice.services.viaje.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.transportsgt.tripservice.exception.ReglaNegocioException;
import org.transportsgt.tripservice.models.Viaje;
import org.transportsgt.tripservice.repositories.ViajeRepository;

import java.sql.SQLException;

/**
 * Guarda un viaje con flush inmediato por dos motivos: los eventos se registran después con la versión ya
 * incrementada (sin flush saldrían con la anterior y Boletos, Alquileres y Reportes los descartarían) y el EXCLUDE
 * de traslape salta aquí, donde se traduce a CONFLICTO_HORARIO, y no al confirmar la transacción.
 */
@Component
@RequiredArgsConstructor
class GuardadoViaje {

    private static final String PG_EXCLUSION = "23P01";

    private final ViajeRepository viajeRepository;

    Viaje guardar(Viaje viaje) {
        try {
            return viajeRepository.saveAndFlush(viaje);
        } catch (DataAccessException e) {
            if (esTraslape(e)) {
                throw new ReglaNegocioException("CONFLICTO_HORARIO",
                        "El bus o el chofer ya tienen un viaje que se traslapa");
            }
            throw e;
        }
    }

    private boolean esTraslape(Throwable e) {
        Throwable causa = e;
        while (causa != null) {
            if (causa instanceof SQLException sql && PG_EXCLUSION.equals(sql.getSQLState())) {
                return true;
            }
            causa = causa.getCause();
        }
        return false;
    }
}
