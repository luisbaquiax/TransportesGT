package org.transportsgt.operationalcostsservice.dominio;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.payload.viajes.DatosLlegada;
import org.transportsgt.operationalcostsservice.exception.ReglaIntegridadException;
import org.transportsgt.operationalcostsservice.models.ConfiguracionDepreciacion;
import org.transportsgt.operationalcostsservice.models.GastoCombustible;
import org.transportsgt.operationalcostsservice.models.PagoChofer;
import org.transportsgt.operationalcostsservice.models.RegistroDepreciacion;
import org.transportsgt.operationalcostsservice.repositories.ConfiguracionDepreciacionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Reglas de negocio para calcular los tres costos de una llegada (§4.5): combustible,
 * depreciación y salario del chofer. Devuelve entidades sin persistir; la orquestación
 * (guardar y publicar) vive en {@code ManejadorLlegadaRegistrada}.
 */
@Component
@RequiredArgsConstructor
public class CalculadoraCostosViaje {

    private final ConfiguracionDepreciacionRepository tarifaRepository;

    /** Combustible: monto = gastoCombustibleTotal reportado por la llegada. */
    public GastoCombustible combustible(DatosLlegada d, LocalDate fechaLlegada) {
        return GastoCombustible.builder()
                .idViaje(d.idViaje())
                .idBus(d.idBus())
                .idSucursal(d.idSucursalBus())
                .monto(d.gastoCombustibleTotal())
                .fechaGasto(fechaLlegada)
                .build();
    }

    /** Depreciación: distancia recorrida × tarifa vigente en la fecha de llegada. */
    public RegistroDepreciacion depreciacion(DatosLlegada d, LocalDate fechaLlegada) {
        ConfiguracionDepreciacion tarifa = tarifaRepository
                .findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(fechaLlegada)
                .orElseThrow(() -> new ReglaIntegridadException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "SIN_TARIFA",
                        "No hay tarifa de depreciación vigente para la fecha " + fechaLlegada));
        BigDecimal distancia = d.distanciaRecorridaKm();
        BigDecimal monto = distancia.multiply(tarifa.getMontoPorKm()).setScale(2, RoundingMode.HALF_UP);
        return RegistroDepreciacion.builder()
                .idViaje(d.idViaje())
                .idBus(d.idBus())
                .idSucursal(d.idSucursalBus())
                .distanciaKm(distancia)
                .montoPorKm(tarifa.getMontoPorKm())
                .monto(monto)
                .fechaCalculo(fechaLlegada)
                .build();
    }

    /** Salario del chofer: salarioBase × multiplicador del tipo de viaje (1.00 / 1.15). */
    public PagoChofer pagoChofer(DatosLlegada d, LocalDate fechaLlegada) {
        BigDecimal multiplicador = PoliticaPagoChofer.para(d.tipoViaje()).multiplicador();
        BigDecimal monto = d.salarioBaseChofer().multiply(multiplicador).setScale(2, RoundingMode.HALF_UP);
        return PagoChofer.builder()
                .idViaje(d.idViaje())
                .idChofer(d.idChofer())
                .idSucursal(d.idSucursalBus())
                .salarioBase(d.salarioBaseChofer())
                .multiplicador(multiplicador)
                .monto(monto)
                .fechaPago(fechaLlegada)
                .build();
    }
}
