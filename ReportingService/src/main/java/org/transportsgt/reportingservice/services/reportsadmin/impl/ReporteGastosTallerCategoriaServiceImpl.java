package org.transportsgt.reportingservice.services.reportsadmin.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.reportingservice.dto.request.FiltroFechas;
import org.transportsgt.reportingservice.dto.response.GrupoReporte;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportsadmin.FilaGastoTaller;
import org.transportsgt.reportingservice.repositories.reportsadmin.ReporteGastosTallerRepository;
import org.transportsgt.reportingservice.services.comun.AlcanceSucursal;
import org.transportsgt.reportingservice.services.comun.ArmadorReporteTabla;
import org.transportsgt.reportingservice.services.comun.FilasTaller;
import org.transportsgt.reportingservice.services.reportsadmin.ReporteGastosTallerCategoriaService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReporteGastosTallerCategoriaServiceImpl implements ReporteGastosTallerCategoriaService {

    static final String TITULO = "Gastos de taller por categoría";

    private final ReporteGastosTallerRepository repository;
    private final AlcanceSucursal alcance;
    private final ArmadorReporteTabla armador;
    private final FilasTaller filasTaller;

    @Override
    public ReporteTabla<FilaGastoTaller> generar(FiltroFechas intervalo, UUID idSucursal, UUID idBus) {
        Map<String, String> otros = new LinkedHashMap<>();
        alcance.sucursalOpcional(idSucursal).ifPresent(s -> otros.put("sucursal", s.getNombre()));
        alcance.busDeSucursal(idBus, idSucursal).ifPresent(b -> otros.put("bus", b.getPlaca()));
        List<FilaGastoTaller> filas = filasTaller.de(
                repository.consultar(intervalo.desde(), intervalo.hasta(), idSucursal, idBus));
        List<GrupoReporte<FilaGastoTaller>> grupos = armador.ordenarPorMontoDesc(
                armador.agrupar(filas, FilaGastoTaller::categoria, FilaGastoTaller::categoria, filasTaller::subtotal),
                g -> armador.sumar(g.filas(), FilaGastoTaller::monto));
        return armador.tabla(TITULO, armador.filtros(intervalo, otros), grupos, filasTaller.subtotal(filas));
    }
}
