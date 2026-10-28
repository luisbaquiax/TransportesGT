package org.transportsgt.notificationservice.eventos.proyeccion;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.mensajeria.consumo.ManejadorEvento;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.flota.DatosChofer;
import org.transportsgt.notificationservice.eventos.ValidacionEventos;
import org.transportsgt.notificationservice.services.proyeccion.ProyeccionChoferesService;

/**
 * Cola {@code notificaciones.flota.q}, llave {@code flota.chofer.registrado}.
 */
@Component
@RequiredArgsConstructor
public class ManejadorChoferRegistrado implements ManejadorEvento<DatosChofer> {

    private final ProyeccionChoferesService proyeccionChoferes;
    private final ValidacionEventos validacion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.CHOFER_REGISTRADO;
    }

    @Override
    public void manejar(SobreEvento<DatosChofer> evento) {
        DatosChofer d = validacion.datos(evento);
        validacion.requeridos(
                evento,
                "idChofer",
                d.idChofer(),
                "idSucursal",
                d.idSucursal(),
                "nombreCompleto",
                d.nombreCompleto(),
                "correo", d.correo(),
                "fechaVencimientoLicencia",
                d.fechaVencimientoLicencia());
        validacion.registrar(evento, proyeccionChoferes.aplicar(d, evento.versionAgregado()));
    }
}
