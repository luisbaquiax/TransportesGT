package org.transportsgt.reportingservice.services.comun;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.reportingservice.dto.response.reportsadmin.FilaGastoTaller;
import org.transportsgt.reportingservice.repositories.reportsadmin.GastoTallerConsulta;

import java.util.List;
import java.util.Map;

/**
 * Filas y subtotales de las líneas de taller (reportes 5 y 12).
 */
@Component
@RequiredArgsConstructor
public class FilasTaller {

    private final ArmadorReporteTabla armador;
    private final TextosReporte textos;

    public List<FilaGastoTaller> de(List<GastoTallerConsulta> consulta) {
        return consulta.stream()
                .map(c -> new FilaGastoTaller(c.getCategoria(), c.getIdBus(), textos.valor(c.getPlaca()),
                        textos.sucursal(c.getSucursal()), c.getFecha(), c.getDescripcion(), c.getTipoLinea(),
                        c.getMonto()))
                .toList();
    }

    public Map<String, Object> subtotal(List<FilaGastoTaller> filas) {
        return armador.totales()
                .dinero("monto", armador.sumar(filas, FilaGastoTaller::monto))
                .cantidad("lineas", filas.size())
                .mapa();
    }
}
