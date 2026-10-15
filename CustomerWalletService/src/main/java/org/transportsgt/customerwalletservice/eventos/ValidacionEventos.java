package org.transportsgt.customerwalletservice.eventos;

import lombok.extern.slf4j.Slf4j;
import org.transportsgt.eventos.EventoNoRecuperableException;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.payload.DatosUsuario;

/**
 * Validaciones y logs comunes de los manejadores. Un dato imposible es un error no recuperable: el evento va
 * directo a la DLQ, sin reintentos. Los logs nunca llevan el payload, solo {@code idEvento} e {@code idCorrelacion}.
 */
@Slf4j
final class ValidacionEventos {

    private ValidacionEventos() {
    }

    static void usuario(SobreEvento<DatosUsuario> evento) {
        DatosUsuario datos = evento.datos();
        if (datos.idUsuario() == null || esVacio(datos.correo()) || esVacio(datos.nombreCompleto())) {
            throw new EventoNoRecuperableException("El evento " + evento.idEvento()
                    + " no trae idUsuario, correo o nombreCompleto");
        }
    }

    static void registrar(SobreEvento<?> evento, boolean aplicado) {
        log.info("{} {} (correlación {}): {}", evento.tipoEvento(), evento.idEvento(), evento.idCorrelacion(),
                aplicado ? "aplicado" : "ignorado");
    }

    private static boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
