package org.transportsgt.privaterentalservice.services.solicitud.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.privaterentalservice.dto.request.SolicitudAlquilerRequest;
import org.transportsgt.privaterentalservice.exception.ReglaNegocioException;
import org.transportsgt.privaterentalservice.exception.SolicitudInvalidaException;
import org.transportsgt.privaterentalservice.models.TarifaAlquiler;
import org.transportsgt.privaterentalservice.repositories.TarifaAlquilerRepository;
import org.transportsgt.privaterentalservice.services.comun.CalendarioGuatemala;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Precio estimado: distancia × precio_por_km × (2 si ida y vuelta) + días × precio_por_dia, con la tarifa vigente hoy.
 */
@Component
@RequiredArgsConstructor
class EstimadorPrecio {

    private final TarifaAlquilerRepository tarifaRepository;
    private final CalculadoraDistancia calculadora;
    private final CalendarioGuatemala calendario;

    /**
     * Con coordenadas la calcula el backend; sin ellas, la indica el solicitante.
     */
    BigDecimal distancia(SolicitudAlquilerRequest req) {
        if (req.tieneCoordenadas()) {
            return calculadora.distanciaCarretera(
                    req.latitudOrigen(), req.longitudOrigen(), req.latitudDestino(), req.longitudDestino());
        }
        if (req.distanciaEstimadaKm() != null) {
            return req.distanciaEstimadaKm();
        }
        throw new SolicitudInvalidaException(
                "Indique las 4 coordenadas o la distanciaEstimadaKm para calcular el precio");
    }

    BigDecimal estimar(BigDecimal distancia, boolean idaYVuelta, OffsetDateTime salida, OffsetDateTime retorno) {
        TarifaAlquiler tarifa = tarifaRepository
                .findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(calendario.hoy())
                .orElseThrow(() -> new ReglaNegocioException("SIN_TARIFA",
                        "No hay tarifa de alquiler vigente: contacte al administrador de sistema"));
        BigDecimal distanciaTotal = idaYVuelta ? distancia.multiply(BigDecimal.valueOf(2)) : distancia;
        BigDecimal porKm = distanciaTotal.multiply(tarifa.getPrecioPorKm());
        BigDecimal porDia = tarifa.getPrecioPorDia().multiply(BigDecimal.valueOf(dias(idaYVuelta, salida, retorno)));
        return porKm.add(porDia).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Días calendario de Guatemala, incluidos el de salida y el de retorno; solo ida cuenta 1.
     */
    long dias(boolean idaYVuelta, OffsetDateTime salida, OffsetDateTime retorno) {
        if (!idaYVuelta || retorno == null) {
            return 1;
        }
        return Math.max(1, ChronoUnit.DAYS.between(calendario.dia(salida), calendario.dia(retorno)) + 1);
    }
}
