package org.transportsgt.reportingservice.services.reportssucursal.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.reportingservice.config.security.UsuarioAutenticado;
import org.transportsgt.reportingservice.dto.request.FiltroFechas;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportssucursal.FilaDepreciacionBus;
import org.transportsgt.reportingservice.models.RepSucursal;
import org.transportsgt.reportingservice.models.RepTarifaDepreciacion;
import org.transportsgt.reportingservice.repositories.RepTarifaDepreciacionRepository;
import org.transportsgt.reportingservice.repositories.reportssucursal.ReporteDepreciacionBusesRepository;
import org.transportsgt.reportingservice.services.comun.AlcanceSucursal;
import org.transportsgt.reportingservice.services.comun.ArmadorReporteTabla;
import org.transportsgt.reportingservice.services.reportssucursal.ReporteDepreciacionBusesService;
import org.transportsgt.reportingservice.utils.Dinero;
import org.transportsgt.reportingservice.utils.Fechas;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReporteDepreciacionBusesServiceImpl implements ReporteDepreciacionBusesService {

    static final String TITULO = "Depreciación por bus";

    private final ReporteDepreciacionBusesRepository repository;
    private final RepTarifaDepreciacionRepository tarifaRepository;
    private final AlcanceSucursal alcance;
    private final ArmadorReporteTabla armador;
    private final Dinero dinero;
    private final Fechas fechas;

    @Override
    public ReporteTabla<FilaDepreciacionBus> generar(UsuarioAutenticado usuario, UUID idSucursal,
                                                     FiltroFechas intervalo) {
        RepSucursal sucursal = alcance.sucursalDelReporte(usuario, idSucursal);
        BigDecimal tarifaActual = tarifaRepository
                .findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(fechas.hoy())
                .map(RepTarifaDepreciacion::getMontoPorKm)
                .orElse(null);
        List<FilaDepreciacionBus> filas = repository.consultar(sucursal.getId(), intervalo.desde(), intervalo.hasta())
                .stream()
                .map(c -> new FilaDepreciacionBus(c.getIdBus(), c.getPlaca(), c.getKmRecorridos(),
                        tarifaActual == null ? null : dinero.escalar(tarifaActual),
                        dinero.escalar(c.getDepreciacionAcumulada()),
                        dinero.dividir(c.getDepreciacionAcumulada(), c.getKmRecorridos())))
                .toList();
        BigDecimal km = filas.stream().map(FilaDepreciacionBus::kmRecorridos)
                .reduce(BigDecimal.ZERO, (a, b) -> b == null ? a : a.add(b));
        BigDecimal acumulada = armador.sumar(filas, FilaDepreciacionBus::depreciacionAcumulada);
        Map<String, Object> totales = armador.totales()
                .decimal("kmRecorridos", km)
                .dinero("depreciacionAcumulada", acumulada)
                .dinero("promedioPorKm", dinero.dividir(acumulada, km))
                .mapa();
        if (tarifaActual != null) {
            totales.put("montoPorKmActual", dinero.texto(tarifaActual));
        }
        return armador.tabla(TITULO, armador.filtros(intervalo, Map.of("sucursal", sucursal.getNombre())),
                armador.sinAgrupar(filas), totales);
    }
}
