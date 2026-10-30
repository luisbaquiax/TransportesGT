package org.transportsgt.reportingservice.services.comun;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.reportingservice.dto.request.FiltroFechas;
import org.transportsgt.reportingservice.dto.response.FiltrosAplicados;
import org.transportsgt.reportingservice.dto.response.GrupoReporte;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.utils.Dinero;
import org.transportsgt.reportingservice.utils.Fechas;

import java.math.BigDecimal;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * Agrupa filas planas, calcula subtotales y arma el {@link ReporteTabla} común a JSON y PDF.
 */
@Component
@RequiredArgsConstructor
public class ArmadorReporteTabla {

    private static final Locale ESPANOL = Locale.forLanguageTag("es-GT");

    private final Dinero dinero;
    private final Fechas fechas;

    public <F> ReporteTabla<F> tabla(String titulo, FiltrosAplicados filtros, List<GrupoReporte<F>> grupos,
                                     Map<String, Object> totales) {
        return new ReporteTabla<>(titulo, filtros, fechas.ahora(), grupos, totales);
    }

    public FiltrosAplicados filtros(FiltroFechas intervalo, Map<String, String> otros) {
        return new FiltrosAplicados(intervalo.desde(), intervalo.hasta(), otros);
    }

    /**
     * Agrupa conservando el orden en que llegan las filas (la consulta ya las ordena por el campo del grupo).
     *
     * @param clave      id del grupo de cada fila
     * @param titulo     nombre legible del grupo
     * @param subtotales subtotales a partir de las filas del grupo
     */
    public <F> List<GrupoReporte<F>> agrupar(List<F> filas, Function<F, String> clave, Function<F, String> titulo,
                                             Function<List<F>, Map<String, Object>> subtotales) {
        Map<String, List<F>> porClave = new LinkedHashMap<>();
        Map<String, String> titulos = new LinkedHashMap<>();
        for (F fila : filas) {
            String k = clave.apply(fila);
            porClave.computeIfAbsent(k, x -> new ArrayList<>()).add(fila);
            titulos.putIfAbsent(k, titulo.apply(fila));
        }
        List<GrupoReporte<F>> grupos = new ArrayList<>();
        porClave.forEach((k, filasGrupo) ->
                grupos.add(new GrupoReporte<>(k, titulos.get(k), filasGrupo, null, subtotales.apply(filasGrupo))));
        return grupos;
    }

    /**
     * Grupos ordenados alfabéticamente por título (sucursales)
     */
    public <F> List<GrupoReporte<F>> ordenarPorTitulo(List<GrupoReporte<F>> grupos) {
        Collator collator = Collator.getInstance(ESPANOL);
        collator.setStrength(Collator.PRIMARY);
        List<GrupoReporte<F>> ordenados = new ArrayList<>(grupos);
        ordenados.sort(Comparator.comparing(g -> g.titulo() == null ? "" : g.titulo(), collator));
        return ordenados;
    }

    /**
     * Grupos de mayor a menor según un subtotal (categorías o buses con más gasto primero).
     */
    public <F> List<GrupoReporte<F>> ordenarPorMontoDesc(List<GrupoReporte<F>> grupos,
                                                         Function<GrupoReporte<F>, BigDecimal> monto) {
        List<GrupoReporte<F>> ordenados = new ArrayList<>(grupos);
        ordenados.sort(Comparator.comparing(monto).reversed());
        return ordenados;
    }

    /**
     * Grupo de segundo nivel: el grupo padre lleva los subgrupos y sus propios subtotales.
     */
    public <F> GrupoReporte<F> grupoConSubgrupos(String clave, String titulo, List<GrupoReporte<F>> subgrupos,
                                                 Map<String, Object> subtotales) {
        return new GrupoReporte<>(clave, titulo, null, subgrupos, subtotales);
    }

    /**
     * Reportes sin agrupación: un solo grupo sin clave ni subtotales.
     */
    public <F> List<GrupoReporte<F>> sinAgrupar(List<F> filas) {
        return List.of(new GrupoReporte<>(null, null, filas, null, null));
    }

    public Totales totales() {
        return new Totales(dinero);
    }

    public <F> BigDecimal sumar(List<F> filas, Function<F, BigDecimal> monto) {
        BigDecimal total = BigDecimal.ZERO;
        for (F fila : filas) {
            total = total.add(dinero.escalar(monto.apply(fila)));
        }
        return dinero.escalar(total);
    }

    public <F> long contar(List<F> filas, ToLongFunction<F> cantidad) {
        return filas.stream().mapToLong(cantidad).sum();
    }
}
