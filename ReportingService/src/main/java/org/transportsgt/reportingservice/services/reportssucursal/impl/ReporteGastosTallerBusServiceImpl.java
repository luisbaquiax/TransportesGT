package org.transportsgt.reportingservice.services.reportssucursal.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.reportingservice.config.security.UsuarioAutenticado;
import org.transportsgt.reportingservice.dto.request.FiltroFechas;
import org.transportsgt.reportingservice.dto.response.GrupoReporte;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportsadmin.FilaGastoTaller;
import org.transportsgt.reportingservice.models.RepSucursal;
import org.transportsgt.reportingservice.repositories.reportsadmin.ReporteGastosTallerRepository;
import org.transportsgt.reportingservice.services.comun.AlcanceSucursal;
import org.transportsgt.reportingservice.services.comun.ArmadorReporteTabla;
import org.transportsgt.reportingservice.services.comun.FilasTaller;
import org.transportsgt.reportingservice.services.reportssucursal.ReporteGastosTallerBusService;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReporteGastosTallerBusServiceImpl implements ReporteGastosTallerBusService {

    static final String TITULO = "Gastos de taller por bus y categoría";

    private final ReporteGastosTallerRepository repository;
    private final AlcanceSucursal alcance;
    private final ArmadorReporteTabla armador;
    private final FilasTaller filasTaller;

    @Override
    public ReporteTabla<FilaGastoTaller> generar(UsuarioAutenticado usuario, UUID idSucursal, FiltroFechas intervalo,
                                                 UUID idBus) {
        RepSucursal sucursal = alcance.sucursalDelReporte(usuario, idSucursal);
        Map<String, String> otros = new LinkedHashMap<>();
        otros.put("sucursal", sucursal.getNombre());
        alcance.busDeSucursal(idBus, sucursal.getId()).ifPresent(b -> otros.put("bus", b.getPlaca()));
        List<FilaGastoTaller> filas = filasTaller.de(
                repository.consultar(intervalo.desde(), intervalo.hasta(), sucursal.getId(), idBus));

        // Primer nivel: bus; segundo nivel: categoría (cada una con su subtotal)
        List<GrupoReporte<FilaGastoTaller>> porBus = armador.agrupar(filas, f -> String.valueOf(f.idBus()),
                FilaGastoTaller::placa, filasTaller::subtotal);
        List<GrupoReporte<FilaGastoTaller>> grupos = armador.ordenarPorMontoDesc(porBus.stream()
                        .map(bus -> armador.grupoConSubgrupos(bus.clave(), bus.titulo(), categorias(bus.filas()),
                                bus.subtotales()))
                        .toList(),
                g -> armador.sumar(g.subgrupos().stream().flatMap(c -> c.filas().stream()).toList(),
                        FilaGastoTaller::monto));
        return armador.tabla(TITULO, armador.filtros(intervalo, otros), grupos, filasTaller.subtotal(filas));
    }

    private List<GrupoReporte<FilaGastoTaller>> categorias(List<FilaGastoTaller> filasBus) {
        List<FilaGastoTaller> ordenadas = filasBus.stream()
                .sorted(Comparator.comparing(FilaGastoTaller::categoria).thenComparing(FilaGastoTaller::fecha,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return armador.agrupar(ordenadas, FilaGastoTaller::categoria, FilaGastoTaller::categoria,
                filasTaller::subtotal);
    }
}
