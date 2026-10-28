package org.transportsgt.operationalcostsservice.eventos;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.mensajeria.consumo.ManejadorEvento;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.viajes.DatosLlegada;
import org.transportsgt.operationalcostsservice.dominio.CalculadoraCostosViaje;
import org.transportsgt.operationalcostsservice.mappers.CostosMapper;
import org.transportsgt.operationalcostsservice.models.GastoCombustible;
import org.transportsgt.operationalcostsservice.models.PagoChofer;
import org.transportsgt.operationalcostsservice.models.RegistroDepreciacion;
import org.transportsgt.operationalcostsservice.repositories.GastoCombustibleRepository;
import org.transportsgt.operationalcostsservice.repositories.PagoChoferRepository;
import org.transportsgt.operationalcostsservice.repositories.RegistroDepreciacionRepository;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * LlegadaRegistrada: orquesta el cálculo de combustible, depreciación y salario del chofer
 * (§4.5). Las reglas viven en {@link CalculadoraCostosViaje}; aquí solo se persiste y se
 * publica por outbox. Idempotente por UNIQUE(id_viaje) en las tres tablas. Si falta tarifa
 * vigente, lanza SIN_TARIFA (retry).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManejadorLlegadaRegistrada implements ManejadorEvento<DatosLlegada> {

    private final GastoCombustibleRepository combustibleRepository;
    private final RegistroDepreciacionRepository depreciacionRepository;
    private final PagoChoferRepository pagoChoferRepository;
    private final CalculadoraCostosViaje calculadora;
    private final CostosMapper mapper;
    private final EventosCostos eventos;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.LLEGADA_REGISTRADA;
    }

    @Override
    @Transactional
    public void manejar(SobreEvento<DatosLlegada> evento) {
        DatosLlegada d = evento.datos();
        if (combustibleRepository.existsByIdViaje(d.idViaje())) {
            log.debug("Viaje {} ya tiene costos; se omite (idempotencia)", d.idViaje());
            return;
        }

        LocalDate fechaLlegada = d.fechaHoraLlegadaReal().withOffsetSameInstant(ZoneOffset.UTC).toLocalDate();

        GastoCombustible combustible = combustibleRepository.saveAndFlush(
                calculadora.combustible(d, fechaLlegada));
        eventos.gastoCombustibleRegistrado(mapper.toDatosCombustible(combustible));

        RegistroDepreciacion depreciacion = depreciacionRepository.saveAndFlush(
                calculadora.depreciacion(d, fechaLlegada));
        eventos.depreciacionCalculada(mapper.toDatosDepreciacion(depreciacion));

        PagoChofer pago = pagoChoferRepository.saveAndFlush(
                calculadora.pagoChofer(d, fechaLlegada));
        eventos.pagoChoferRegistrado(mapper.toDatosPagoChofer(pago));

        log.info("Costos del viaje {} calculados: comb={} dep={} pago={}",
                d.idViaje(), combustible.getMonto(), depreciacion.getMonto(), pago.getMonto());
    }
}
