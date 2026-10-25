package org.transportsgt.reportingservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Un grupo del reporte (sucursal, categoría o bus) con su subtotal. Un grupo trae filas o subgrupos
 * (p. ej. bus → categoría), nunca ambos.
 *
 * @param clave      id del grupo ({@code null} en reportes sin agrupación)
 * @param titulo     nombre legible del grupo
 * @param filas      detalle del grupo
 * @param subgrupos  segundo nivel de agrupación
 * @param subtotales subtotales del grupo; el dinero viaja como texto con 2 decimales
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GrupoReporte<F>(
        String clave,
        String titulo,
        List<F> filas,
        List<GrupoReporte<F>> subgrupos,
        Map<String, Object> subtotales
) {
}
