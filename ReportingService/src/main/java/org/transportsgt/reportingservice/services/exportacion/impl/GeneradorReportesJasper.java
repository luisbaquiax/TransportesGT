package org.transportsgt.reportingservice.services.exportacion.impl;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.exception.GeneracionReporteException;
import org.transportsgt.reportingservice.services.exportacion.GeneradorPdf;
import org.transportsgt.reportingservice.services.exportacion.jasper.CatalogoPlantillasJasper;
import org.transportsgt.reportingservice.services.exportacion.jasper.ParametrosReporteJasper;
import org.transportsgt.reportingservice.services.exportacion.jasper.PlantillaJasper;
import org.transportsgt.reportingservice.services.exportacion.reports.ReporteJasper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Genera los PDF usando los datos ya calculados por los servicios de reportes. */
@Component
public class GeneradorReportesJasper implements GeneradorPdf {
    private final Map<String, ReporteJasper> adaptadores;
    private final Map<String, JasperReport> cache = new ConcurrentHashMap<>();
    private final CatalogoPlantillasJasper catalogo;
    private final ParametrosReporteJasper parametros;

    public GeneradorReportesJasper(List<ReporteJasper> adaptadores,
                                   CatalogoPlantillasJasper catalogo,
                                   ParametrosReporteJasper parametros) {
        this.adaptadores = adaptadores.stream().collect(Collectors.toUnmodifiableMap(
                ReporteJasper::nombre, Function.identity()));
        this.catalogo = catalogo;
        this.parametros = parametros;
    }

    @Override
    public byte[] generar(String nombre, ReporteTabla<?> reporte) {
        try {
            PlantillaJasper plantilla = catalogo.obtener(nombre);
            ReporteJasper adaptador = adaptadores.get(nombre);
            if (adaptador == null)
                throw new IllegalArgumentException("No existe adaptador para " + nombre);
            JasperReport compilado = cache.computeIfAbsent(nombre, n -> compilar(plantilla.ruta()));
            JRBeanCollectionDataSource datos = new JRBeanCollectionDataSource(adaptador.adaptar(reporte));
            return JasperExportManager.exportReportToPdf(
                    JasperFillManager.fillReport(compilado, parametros.crear(reporte, plantilla), datos));
        } catch (JRException | RuntimeException ex) {
            if (ex instanceof GeneracionReporteException generacion) throw generacion;
            throw new GeneracionReporteException("No fue posible generar el PDF solicitado", ex);
        }
    }

    private JasperReport compilar(String ruta) {
        try (InputStream entrada = new ClassPathResource(ruta).getInputStream()) {
            return JasperCompileManager.compileReport(entrada);
        } catch (JRException | IOException ex) {
            throw new GeneracionReporteException("No fue posible compilar la plantilla " + ruta, ex);
        }
    }
}
