package org.transportsgt.operationalcostsservice.eventos;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosLlegada;
import org.transportsgt.eventos.payload.TipoViaje;
import org.transportsgt.operationalcostsservice.exception.ReglaIntegridadException;
import org.transportsgt.operationalcostsservice.models.ConfiguracionDepreciacion;
import org.transportsgt.operationalcostsservice.models.GastoCombustible;
import org.transportsgt.operationalcostsservice.models.PagoChofer;
import org.transportsgt.operationalcostsservice.models.RegistroDepreciacion;
import org.transportsgt.operationalcostsservice.repositories.ConfiguracionDepreciacionRepository;
import org.transportsgt.operationalcostsservice.repositories.GastoCombustibleRepository;
import org.transportsgt.operationalcostsservice.repositories.PagoChoferRepository;
import org.transportsgt.operationalcostsservice.repositories.RegistroDepreciacionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * LlegadaRegistrada: genera combustible, depreciación y salario del chofer (§4.5). Idempotente
 * por UNIQUE(id_viaje) en las tres tablas. Si falta tarifa vigente, lanza SIN_TARIFA (retry).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManejadorLlegadaRegistrada implements ManejadorEvento<DatosLlegada> {

    private final GastoCombustibleRepository combustibleRepository;
    private final RegistroDepreciacionRepository depreciacionRepository;
    private final PagoChoferRepository pagoChoferRepository;
    private final ConfiguracionDepreciacionRepository tarifaRepository;
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

        // 1. Combustible: monto = gastoCombustibleTotal del evento
        GastoCombustible combustible = GastoCombustible.builder()
                .idViaje(d.idViaje())
                .idBus(d.idBus())
                .idSucursal(d.idSucursalBus())
                .monto(d.gastoCombustibleTotal())
                .fechaGasto(fechaLlegada)
                .build();
        combustible = combustibleRepository.saveAndFlush(combustible);
        eventos.gastoCombustibleRegistrado(new org.transportsgt.eventos.payload.DatosGastoCombustible(
                combustible.getId(), combustible.getIdViaje(), combustible.getIdBus(),
                combustible.getIdSucursal(), combustible.getMonto(), combustible.getFechaGasto()));

        // 2. Depreciación: distancia × tarifa vigente en la fecha de llegada
        ConfiguracionDepreciacion tarifa = tarifaRepository
                .findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(fechaLlegada)
                .orElseThrow(() -> new ReglaIntegridadException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "SIN_TARIFA",
                        "No hay tarifa de depreciación vigente para la fecha " + fechaLlegada));
        BigDecimal distancia = d.distanciaRecorridaKm();
        BigDecimal monto = distancia.multiply(tarifa.getMontoPorKm()).setScale(2, RoundingMode.HALF_UP);
        RegistroDepreciacion depreciacion = RegistroDepreciacion.builder()
                .idViaje(d.idViaje())
                .idBus(d.idBus())
                .idSucursal(d.idSucursalBus())
                .distanciaKm(distancia)
                .montoPorKm(tarifa.getMontoPorKm())
                .monto(monto)
                .fechaCalculo(fechaLlegada)
                .build();
        depreciacion = depreciacionRepository.saveAndFlush(depreciacion);
        eventos.depreciacionCalculada(new org.transportsgt.eventos.payload.DatosDepreciacion(
                depreciacion.getId(), depreciacion.getIdViaje(), depreciacion.getIdBus(),
                depreciacion.getIdSucursal(), depreciacion.getDistanciaKm(),
                depreciacion.getMontoPorKm(), depreciacion.getMonto(), depreciacion.getFechaCalculo()));

        // 3. Salario del chofer: salarioBase × 1.00 (regular) o ×1.15 (alquiler privado)
        BigDecimal multiplicador = d.tipoViaje() == TipoViaje.ALQUILER_PRIVADO
                ? new BigDecimal("1.15") : new BigDecimal("1.00");
        BigDecimal montoPago = d.salarioBaseChofer().multiply(multiplicador).setScale(2, RoundingMode.HALF_UP);
        PagoChofer pago = PagoChofer.builder()
                .idViaje(d.idViaje())
                .idChofer(d.idChofer())
                .idSucursal(d.idSucursalBus())
                .salarioBase(d.salarioBaseChofer())
                .multiplicador(multiplicador)
                .monto(montoPago)
                .fechaPago(fechaLlegada)
                .build();
        pago = pagoChoferRepository.saveAndFlush(pago);
        eventos.pagoChoferRegistrado(new org.transportsgt.eventos.payload.DatosPagoChofer(
                pago.getId(), pago.getIdViaje(), pago.getIdChofer(), pago.getIdSucursal(),
                pago.getSalarioBase(), pago.getMultiplicador(), pago.getMonto(), pago.getFechaPago()));

        log.info("Costos del viaje {} calculados: comb={} dep={} pago={}",
                d.idViaje(), combustible.getMonto(), depreciacion.getMonto(), pago.getMonto());
    }
}
