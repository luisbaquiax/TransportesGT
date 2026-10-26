package org.transportsgt.reportingservice.services.proyecciones;

import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.payload.DatosAlquilerCancelado;
import org.transportsgt.eventos.payload.DatosAlquilerConfirmado;
import org.transportsgt.eventos.payload.DatosBoletosAnulados;
import org.transportsgt.eventos.payload.DatosVentaBoletos;

/**
 * Hechos de {@code rep_ingreso} (eventos de Boletos y Alquileres).
 */
public interface ProyeccionIngresosService {

    /** VentaBoletosRegistrada: ingreso VENTA_BOLETOS. */
    void registrarVenta(SobreEvento<DatosVentaBoletos> evento);

    /** BoletosAnulados: anula la venta; si aún no llegó, error transitorio. */
    void anularVenta(SobreEvento<DatosBoletosAnulados> evento);

    /** AlquilerConfirmado: ingreso ALQUILER. */
    void registrarAlquiler(SobreEvento<DatosAlquilerConfirmado> evento);

    /** AlquilerCancelado: anula el alquiler; si aún no llegó, error transitorio. */
    void cancelarAlquiler(SobreEvento<DatosAlquilerCancelado> evento);
}
