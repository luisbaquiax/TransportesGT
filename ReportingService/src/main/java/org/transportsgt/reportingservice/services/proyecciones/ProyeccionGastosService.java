package org.transportsgt.reportingservice.services.proyecciones;

import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.payload.costos.DatosDepreciacion;
import org.transportsgt.eventos.payload.costos.DatosGastoCombustible;
import org.transportsgt.eventos.payload.costos.DatosGastoTaller;
import org.transportsgt.eventos.payload.costos.DatosPagoChofer;
import org.transportsgt.eventos.payload.costos.DatosTarifaDepreciacion;

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
