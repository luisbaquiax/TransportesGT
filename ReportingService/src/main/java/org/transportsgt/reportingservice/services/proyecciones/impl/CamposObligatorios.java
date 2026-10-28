package org.transportsgt.reportingservice.services.proyecciones.impl;

import org.springframework.stereotype.Component;
import org.transportsgt.eventos.mensajeria.excepciones.EventoNoRecuperableException;
import org.transportsgt.eventos.contrato.SobreEvento;

/**
 * Un evento sin sus datos clave es un mensaje venenoso: va a la DLQ de inmediato, sin reintentos.
 */
@Component
class CamposObligatorios {

    <T> T datos(SobreEvento<T> evento) {
        if (evento.datos() == null) {
            throw new EventoNoRecuperableException("El evento " + evento.idEvento() + " no trae datos");
        }
        return evento.datos();
    }

    void exigir(SobreEvento<?> evento, String campo, Object valor) {
        if (valor == null) {
            throw new EventoNoRecuperableException("El evento " + evento.idEvento() + " no trae " + campo);
        }
    }
}
