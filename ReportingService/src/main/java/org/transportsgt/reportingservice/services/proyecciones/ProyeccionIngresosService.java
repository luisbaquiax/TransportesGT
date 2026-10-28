package org.transportsgt.reportingservice.services.proyecciones;

import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.payload.alquileres.DatosAlquilerCancelado;
import org.transportsgt.eventos.payload.alquileres.DatosAlquilerConfirmado;
import org.transportsgt.eventos.payload.boletos.DatosBoletosAnulados;
import org.transportsgt.eventos.payload.boletos.DatosVentaBoletos;

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
