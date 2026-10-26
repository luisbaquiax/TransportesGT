package org.transportsgt.reportingservice.services.reportsadmin.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.reportingservice.dto.request.FiltroFechas;
import org.transportsgt.reportingservice.dto.response.GrupoReporte;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportsadmin.FilaGasto;
import org.transportsgt.reportingservice.models.RepSucursal;
import org.transportsgt.reportingservice.repositories.reportsadmin.GastoConsulta;
import org.transportsgt.reportingservice.repositories.reportsadmin.ReporteGastosRepository;
import org.transportsgt.reportingservice.services.comun.AlcanceSucursal;
import org.transportsgt.reportingservice.services.comun.ArmadorReporteTabla;
import org.transportsgt.reportingservice.services.comun.TextosReporte;
import org.transportsgt.reportingservice.services.comun.Totales;
import org.transportsgt.reportingservice.services.reportsadmin.ReporteGastosService;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReporteGastosServiceImpl implements ReporteGastosService {

    static final String TITULO = "Reporte de gastos por sucursal";
    /** Tipos del desglose, en el orden en que se muestran (taller separado en mano de obra y repuesto). */
    static final List<String> TIPOS = List.of("COMBUSTIBLE", "TALLER", "REPUESTO", "DEPRECIACION", "SALARIO_CHOFER");

    private final ReporteGastosRepository repository;
    private final AlcanceSucursal alcance;
    private final ArmadorReporteTabla armador;
    private final TextosReporte textos;

    @Override
    public ReporteTabla<FilaGasto> generar(FiltroFechas intervalo, UUID idSucursal) {
        Optional<RepSucursal> sucursal = alcance.sucursalOpcional(idSucursal);
        List<FilaGasto> filas = repository.consultar(intervalo.desde(), intervalo.hasta(), idSucursal).stream()
                .map(this::aFila)
                .toList();
        List<GrupoReporte<FilaGasto>> grupos = armador.ordenarPorTitulo(armador.agrupar(filas,
                f -> String.valueOf(f.idSucursal()), FilaGasto::sucursal, this::subtotales));
        Map<String, String> otros = new LinkedHashMap<>();
        sucursal.ifPresent(s -> otros.put("sucursal", s.getNombre()));
        return armador.tabla(TITULO, armador.filtros(intervalo, otros), grupos, subtotales(filas));
    }

    /** Total y desglose por tipo de gasto (los tipos sin gastos salen en cero). */
    private Map<String, Object> subtotales(List<FilaGasto> filas) {
        Totales totales = armador.totales().dinero("monto", armador.sumar(filas, FilaGasto::monto));
        for (String tipo : TIPOS) {
            totales.dinero(tipo, armador.sumar(filas.stream().filter(f -> tipo.equals(f.tipo())).toList(),
                    FilaGasto::monto));
        }
        return totales.cantidad("gastos", filas.size()).mapa();
    }

    private FilaGasto aFila(GastoConsulta c) {
        return new FilaGasto(c.getIdSucursal(), textos.sucursal(c.getSucursal()), c.getFecha(), tipo(c),
                c.getIdReferencia(), c.getPlaca(), descripcion(c), c.getMonto());
    }

    String tipo(GastoConsulta c) {
        if ("TALLER".equals(c.getTipoGasto())) {
            return "REPUESTO".equals(c.getTipoLinea()) ? "REPUESTO" : "TALLER";
        }
        return c.getTipoGasto();
    }

    String descripcion(GastoConsulta c) {
        return switch (c.getTipoGasto()) {
            case "TALLER" -> c.getDescripcion() == null
                    ? textos.valor(c.getCategoria())
                    : textos.valor(c.getCategoria()) + ": " + c.getDescripcion();
            case "SALARIO_CHOFER" -> c.getChofer() == null ? "Salario del chofer" : "Salario de " + c.getChofer();
            case "DEPRECIACION" -> "Depreciación: " + numero(c.getDistanciaKm()) + " km × Q "
                    + numero(c.getMontoPorKm()) + " por km";
            default -> "Combustible del viaje";
        };
    }

    private String numero(BigDecimal valor) {
        return valor == null ? TextosReporte.SIN_DATO : valor.stripTrailingZeros().toPlainString();
    }
}
