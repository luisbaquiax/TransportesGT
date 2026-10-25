package org.transportsgt.reportingservice.services.reportsadmin.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.reportingservice.dto.request.FiltroFechas;
import org.transportsgt.reportingservice.dto.response.GrupoReporte;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportsadmin.FilaIngreso;
import org.transportsgt.reportingservice.models.RepSucursal;
import org.transportsgt.reportingservice.models.TipoIngreso;
import org.transportsgt.reportingservice.repositories.reportsadmin.IngresoConsulta;
import org.transportsgt.reportingservice.repositories.reportsadmin.ReporteIngresosRepository;
import org.transportsgt.reportingservice.services.comun.AlcanceSucursal;
import org.transportsgt.reportingservice.services.comun.ArmadorReporteTabla;
import org.transportsgt.reportingservice.services.comun.TextosReporte;
import org.transportsgt.reportingservice.services.reportsadmin.ReporteIngresosService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReporteIngresosServiceImpl implements ReporteIngresosService {

    static final String TITULO = "Reporte de ingresos por sucursal";

    private final ReporteIngresosRepository repository;
    private final AlcanceSucursal alcance;
    private final ArmadorReporteTabla armador;
    private final TextosReporte textos;

    @Override
    public ReporteTabla<FilaIngreso> generar(FiltroFechas intervalo, UUID idSucursal) {
        Optional<RepSucursal> sucursal = alcance.sucursalOpcional(idSucursal);
        List<FilaIngreso> filas = repository.consultar(intervalo.desde(), intervalo.hasta(), idSucursal).stream()
                .map(this::aFila)
                .toList();
        List<GrupoReporte<FilaIngreso>> grupos = armador.ordenarPorTitulo(armador.agrupar(filas,
                f -> String.valueOf(f.idSucursal()), FilaIngreso::sucursal, this::subtotales));
        Map<String, String> otros = new LinkedHashMap<>();
        sucursal.ifPresent(s -> otros.put("sucursal", s.getNombre()));
        return armador.tabla(TITULO, armador.filtros(intervalo, otros), grupos, subtotales(filas));
    }

    private Map<String, Object> subtotales(List<FilaIngreso> filas) {
        return armador.totales()
                .dinero("monto", armador.sumar(filas, FilaIngreso::monto))
                .cantidad("ingresos", filas.size())
                .cantidad("boletos", armador.contar(filas,
                        f -> f.cantidadBoletos() == null ? 0 : f.cantidadBoletos()))
                .mapa();
    }

    private FilaIngreso aFila(IngresoConsulta c) {
        String descripcion = TipoIngreso.VENTA_BOLETOS.name().equals(c.getTipo())
                ? textos.trayecto(c.getRutaOrigen(), c.getRutaDestino())
                : textos.trayecto(c.getOrigen(), c.getDestino());
        return new FilaIngreso(c.getIdSucursal(), textos.sucursal(c.getSucursal()), c.getFecha(), c.getTipo(),
                c.getReferencia(), descripcion, c.getCliente(), c.getNit(), c.getCantidadBoletos(), c.getCanal(),
                c.getMonto());
    }
}
