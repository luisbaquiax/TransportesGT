package org.transportsgt.reportingservice.services.proyecciones.impl;

import org.springframework.stereotype.Component;
import org.transportsgt.eventos.SobreEvento;

/**
 * Versión de un evento instantánea; sin versión cuenta como 0 (solo crea la fila, nunca reemplaza).
 */
@Component
class VersionAgregado {

    long de(SobreEvento<?> evento) {
        return evento.versionAgregado() == null ? 0L : evento.versionAgregado();
    }
}
