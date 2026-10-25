package org.transportsgt.reportingservice.services.proyecciones;

import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.payload.DatosDepreciacion;
import org.transportsgt.eventos.payload.DatosGastoCombustible;
import org.transportsgt.eventos.payload.DatosGastoTaller;
import org.transportsgt.eventos.payload.DatosPagoChofer;
import org.transportsgt.eventos.payload.DatosTarifaDepreciacion;

/**
 * Hechos de {@code rep_gasto} y tarifa de depreciación (eventos de Costos).
 */
public interface ProyeccionGastosService {

    /** GastoCombustibleRegistrado: gasto COMBUSTIBLE. */
    void registrarCombustible(SobreEvento<DatosGastoCombustible> evento);

    /** DepreciacionCalculada: gasto DEPRECIACION con kilómetros y tarifa aplicada. */
    void registrarDepreciacion(SobreEvento<DatosDepreciacion> evento);

    /** GastoTallerRegistrado: un gasto TALLER por línea. */
    void registrarTaller(SobreEvento<DatosGastoTaller> evento);

    /** PagoChoferRegistrado: gasto SALARIO_CHOFER. */
    void registrarPagoChofer(SobreEvento<DatosPagoChofer> evento);

    /** TarifaDepreciacionCambiada: upsert por {@code vigente_desde}. */
    void cambiarTarifa(SobreEvento<DatosTarifaDepreciacion> evento);
}
