package org.transportsgt.reportingservice.services.proyecciones;

import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.payload.DatosBus;
import org.transportsgt.eventos.payload.DatosChofer;

/**
 * Proyecciones {@code rep_bus} y {@code rep_chofer} (eventos de Flota).
 */
public interface ProyeccionFlotaService {

    /** BusRegistrado / BusActualizado: upsert por versión; el kilometraje solo se fija al crear la fila. */
    void aplicarBus(SobreEvento<DatosBus> evento);

    /** ChoferRegistrado / ChoferActualizado: upsert por versión. */
    void aplicarChofer(SobreEvento<DatosChofer> evento);
}
