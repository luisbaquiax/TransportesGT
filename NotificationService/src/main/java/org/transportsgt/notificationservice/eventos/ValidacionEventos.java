package org.transportsgt.notificationservice.eventos;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.mensajeria.excepciones.EventoNoRecuperableException;
import org.transportsgt.eventos.contrato.SobreEvento;


/**
 * Validaciones y logs comunes de los manejadores.
 */
@Slf4j
@Component
public class ValidacionEventos {

    /**
     * El payload del evento; sin {@code datos} el evento es inválido.
     */
    public <T> T datos(SobreEvento<T> evento) {
        if (evento.datos() == null) {
            throw invalido(evento, "datos");
        }
        return evento.datos();
    }

    /**
     * @param campos pares (nombre, valor): {@code requeridos(evento, "idUsuario", d.idUsuario(), "correo", d.correo())}
     */
    public void requeridos(SobreEvento<?> evento, Object... campos) {
        for (int i = 0; i + 1 < campos.length; i += 2) {
            Object valor = campos[i + 1];
            if (valor == null || (valor instanceof String texto && texto.isBlank())) {
                throw invalido(evento, String.valueOf(campos[i]));
            }
        }
    }

    public void registrar(SobreEvento<?> evento, String resultado) {
        log.info("{} {} (correlación {}): {}", evento.tipoEvento(), evento.idEvento(), evento.idCorrelacion(), resultado);
    }

    public void registrar(SobreEvento<?> evento, boolean aplicado) {
        registrar(evento, aplicado ? "aplicado" : "ignorado");
    }

    private EventoNoRecuperableException invalido(SobreEvento<?> evento, String faltante) {
        return new EventoNoRecuperableException("El evento " + evento.idEvento() + " no trae " + faltante);
    }

}
