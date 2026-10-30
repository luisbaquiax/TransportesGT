package org.transportsgt.reportingservice.services.exportacion.reports;

import org.springframework.stereotype.Component;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportsadmin.FilaGastoTaller;

import java.math.BigDecimal;
import java.util.List;

@Component
public class ConversorFilasTallerJasper {
    private final SoporteAdaptadoresJasper soporte;
    private final FormateadorReporteJasper formato;

    public ConversorFilasTallerJasper(SoporteAdaptadoresJasper soporte, FormateadorReporteJasper formato) {
        this.soporte = soporte;
        this.formato = formato;
    }

    public List<FilaReportePdf> convertir(ReporteTabla<?> reporte) {
        return soporte.aplanar(reporte, FilaGastoTaller.class, fila -> new DatosFilaJasper(
                fila.monto(), BigDecimal.ZERO, formato.texto(fila.placa()), formato.texto(fila.sucursal()),
                formato.texto(fila.fecha()), fila.descripcion(), fila.tipoLinea(), formato.moneda(fila.monto())));
    }
}
