package org.transportsgt.ticketservice.eventos;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.EventoNoRecuperableException;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.payload.DatosPerfilClienteActualizado;
import org.transportsgt.eventos.payload.DatosViaje;
import org.transportsgt.eventos.payload.DatosViajeEliminado;
import org.transportsgt.eventos.payload.TipoViaje;

/**
 * Validaciones y logs comunes de los manejadores.
 */
@Slf4j
@Component
class ValidacionEventos {

    /**
     * Un viaje regular necesita todo lo que se muestra y se cobra: ruta, origen, horarios y precio.
     */
    void viaje(SobreEvento<DatosViaje> evento) {
        DatosViaje datos = evento.datos();
        if (datos.idViaje() == null || datos.tipoViaje() == null || datos.estado() == null) {
            throw invalido(evento, "idViaje, tipoViaje o estado");
        }
        if (datos.tipoViaje() == TipoViaje.REGULAR && (datos.idRuta() == null || datos.idSucursalOrigen() == null
                || datos.fechaHoraSalida() == null || datos.fechaHoraLlegadaEstimada() == null
                || datos.precioBoleto() == null || esVacio(datos.nombreOrigen()) || esVacio(datos.nombreDestino()))) {
            throw invalido(evento, "los datos de un viaje regular (ruta, origen, horarios, precio)");
        }
    }

    void viajeEliminado(SobreEvento<DatosViajeEliminado> evento) {
        if (evento.datos().idViaje() == null) {
            throw invalido(evento, "idViaje");
        }
    }

    void perfil(SobreEvento<DatosPerfilClienteActualizado> evento) {
        DatosPerfilClienteActualizado datos = evento.datos();
        if (datos.idUsuario() == null || esVacio(datos.nombreCompleto())) {
            throw invalido(evento, "idUsuario o nombreCompleto");
        }
    }

    void registrar(SobreEvento<?> evento, boolean aplicado) {
        log.info("{} {} (correlación {}): {}", evento.tipoEvento(), evento.idEvento(), evento.idCorrelacion(),
                aplicado ? "aplicado" : "ignorado");
    }

    private EventoNoRecuperableException invalido(SobreEvento<?> evento, String faltante) {
        return new EventoNoRecuperableException("El evento " + evento.idEvento() + " no trae " + faltante);
    }

    private boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
