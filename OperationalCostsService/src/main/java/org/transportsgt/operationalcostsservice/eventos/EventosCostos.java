package org.transportsgt.operationalcostsservice.eventos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.RegistroOutbox;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosDepreciacion;
import org.transportsgt.eventos.payload.DatosGastoCombustible;
import org.transportsgt.eventos.payload.DatosGastoTaller;
import org.transportsgt.eventos.payload.DatosPagoChofer;
import org.transportsgt.eventos.payload.DatosTarifaDepreciacion;

import java.util.UUID;

/** Guarda los eventos de costos en el outbox (misma transacción que el cambio). */
@Component
@RequiredArgsConstructor
public class EventosCostos {

    private final RegistroOutbox outbox;

    public void gastoCombustibleRegistrado(DatosGastoCombustible datos) {
        outbox.registrar(TipoEvento.GASTO_COMBUSTIBLE_REGISTRADO, datos.idGasto(), null, datos);
    }

    public void depreciacionCalculada(DatosDepreciacion datos) {
        outbox.registrar(TipoEvento.DEPRECIACION_CALCULADA, datos.idDepreciacion(), null, datos);
    }

    public void gastoTallerRegistrado(DatosGastoTaller datos) {
        outbox.registrar(TipoEvento.GASTO_TALLER_REGISTRADO, datos.idGasto(), null, datos);
    }

    public void tarifaDepreciacionCambiada(DatosTarifaDepreciacion datos) {
        outbox.registrar(TipoEvento.TARIFA_DEPRECIACION_CAMBIADA, UUID.nameUUIDFromBytes(
                datos.vigenteDesde().toString().getBytes()), null, datos);
    }

    public void pagoChoferRegistrado(DatosPagoChofer datos) {
        outbox.registrar(TipoEvento.PAGO_CHOFER_REGISTRADO, datos.idPago(), null, datos);
    }
}
