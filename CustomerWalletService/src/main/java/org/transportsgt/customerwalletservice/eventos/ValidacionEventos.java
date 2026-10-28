package org.transportsgt.customerwalletservice.eventos;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.mensajeria.excepciones.EventoNoRecuperableException;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.payload.identidad.DatosUsuario;

/**
 * Validaciones y logs comunes de los manejadores. Un dato imposible es un error no recuperable: el evento va
 * directo a la DLQ, sin reintentos. Los logs nunca llevan el payload, solo {@code idEvento} e {@code idCorrelacion}.
 */
@Slf4j
@Component
class ValidacionEventos {

    void usuario(SobreEvento<DatosUsuario> evento) {
        DatosUsuario datos = evento.datos();
        if (datos.idUsuario() == null || esVacio(datos.correo()) || esVacio(datos.nombreCompleto())) {
            throw new EventoNoRecuperableException("El evento " + evento.idEvento()
                    + " no trae idUsuario, correo o nombreCompleto");
        }
    }

    void registrar(SobreEvento<?> evento, boolean aplicado) {
        log.info("{} {} (correlación {}): {}", evento.tipoEvento(), evento.idEvento(), evento.idCorrelacion(),
                aplicado ? "aplicado" : "ignorado");
    }

    private boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
