package org.transportsgt.reportingservice.services.reportsadmin.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.reportingservice.dto.request.FiltroFechas;
import org.transportsgt.reportingservice.dto.response.GrupoReporte;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportsadmin.FilaGanancia;
import org.transportsgt.reportingservice.models.RepSucursal;
import org.transportsgt.reportingservice.repositories.reportsadmin.ReporteGananciasRepository;
import org.transportsgt.reportingservice.services.comun.AlcanceSucursal;
import org.transportsgt.reportingservice.services.comun.ArmadorReporteTabla;
import org.transportsgt.reportingservice.services.reportsadmin.ReporteGananciasService;
import org.transportsgt.reportingservice.utils.Dinero;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReporteGananciasServiceImpl implements ReporteGananciasService {

    static final String TITULO = "Reporte de ganancias por sucursal";

    private final ReporteGananciasRepository repository;
    private final AlcanceSucursal alcance;
    private final ArmadorReporteTabla armador;
    private final Dinero dinero;

    @Override
    public ReporteTabla<FilaGanancia> generar(FiltroFechas intervalo, UUID idSucursal) {
        Optional<RepSucursal> sucursal = alcance.sucursalOpcional(idSucursal);
        List<FilaGanancia> filas = repository.consultar(
                        intervalo.desde(),
                        intervalo.hasta(),
                        idSucursal).stream()
                .map(c -> new FilaGanancia(c.getIdSucursal(),
                        c.getSucursal(),
                        dinero.escalar(c.getIngresos()),
                        dinero.escalar(c.getGastos()),
                        dinero.escalar(c.getGanancia())))
                .toList();
        // Una sucursal por grupo: así el orden alfabético usa la misma regla que los demás reportes
        List<GrupoReporte<FilaGanancia>> grupos = armador.ordenarPorTitulo(armador.agrupar(filas,
                f -> String.valueOf(f.idSucursal()), FilaGanancia::sucursal, g -> null));
        List<FilaGanancia> ordenadas = grupos.stream().flatMap(g -> g.filas().stream()).toList();
        Map<String, Object> totales = armador.totales()
                .dinero("ingresos", armador.sumar(ordenadas, FilaGanancia::ingresos))
                .dinero("gastos", armador.sumar(ordenadas, FilaGanancia::gastos))
                .dinero("ganancia", armador.sumar(ordenadas, FilaGanancia::ganancia))
                .mapa();
        Map<String, String> otros = new LinkedHashMap<>();
        sucursal.ifPresent(s -> otros.put("sucursal", s.getNombre()));
        return armador.tabla(TITULO, armador.filtros(intervalo, otros), armador.sinAgrupar(ordenadas), totales);
    }
}
