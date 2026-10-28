package org.transportsgt.operationalcostsservice.eventos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.mensajeria.publicacion.RegistroOutbox;
import org.transportsgt.eventos.payload.costos.DatosDepreciacion;
import org.transportsgt.eventos.payload.costos.DatosGastoCombustible;
import org.transportsgt.eventos.payload.costos.DatosGastoTaller;
import org.transportsgt.eventos.payload.costos.DatosPagoChofer;
import org.transportsgt.eventos.payload.costos.DatosTarifaDepreciacion;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventosCostosTest {

    @Mock RegistroOutbox outbox;

    @InjectMocks EventosCostos eventos;

    private final UUID id = UUID.randomUUID();

    @Test
    void gastosYDepreciacionUsanSuIdComoAgregado() {
        DatosGastoCombustible combustible = mock(DatosGastoCombustible.class);
        DatosDepreciacion depreciacion = mock(DatosDepreciacion.class);
        DatosGastoTaller taller = mock(DatosGastoTaller.class);
        DatosPagoChofer pago = mock(DatosPagoChofer.class);
        when(combustible.idGasto()).thenReturn(id);
        when(depreciacion.idDepreciacion()).thenReturn(id);
        when(taller.idGasto()).thenReturn(id);
        when(pago.idPago()).thenReturn(id);

        eventos.gastoCombustibleRegistrado(combustible);
        eventos.depreciacionCalculada(depreciacion);
        eventos.gastoTallerRegistrado(taller);
        eventos.pagoChoferRegistrado(pago);

        verify(outbox).registrar(TipoEvento.GASTO_COMBUSTIBLE_REGISTRADO, id, null, combustible);
        verify(outbox).registrar(TipoEvento.DEPRECIACION_CALCULADA, id, null, depreciacion);
        verify(outbox).registrar(TipoEvento.GASTO_TALLER_REGISTRADO, id, null, taller);
        verify(outbox).registrar(TipoEvento.PAGO_CHOFER_REGISTRADO, id, null, pago);
    }

    @Test
    void tarifaUsaUnIdDerivadoDeSuFechaDeVigencia() {
        DatosTarifaDepreciacion tarifa = mock(DatosTarifaDepreciacion.class);
        when(tarifa.vigenteDesde()).thenReturn(LocalDate.parse("2026-11-01"));

        eventos.tarifaDepreciacionCambiada(tarifa);

        verify(outbox).registrar(org.mockito.ArgumentMatchers.eq(TipoEvento.TARIFA_DEPRECIACION_CAMBIADA),
                org.mockito.ArgumentMatchers.eq(UUID.nameUUIDFromBytes("2026-11-01".getBytes())), isNull(),
                org.mockito.ArgumentMatchers.eq(tarifa));
    }
}
