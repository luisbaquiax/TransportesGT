package org.transportsgt.reportingservice.services.reportsadmin.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.reportingservice.dto.request.FiltroFechas;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportsadmin.FilaRutaDemandada;
import org.transportsgt.reportingservice.repositories.reportsadmin.RutaDemandadaConsulta;
import org.transportsgt.reportingservice.repositories.reportsadmin.ReporteRutasDemandadasRepository;
import org.transportsgt.reportingservice.services.comun.ArmadorReporteTabla;
import org.transportsgt.reportingservice.services.comun.TextosReporte;
import org.transportsgt.reportingservice.services.reportsadmin.ReporteRutasDemandadasService;
import org.transportsgt.reportingservice.utils.Dinero;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReporteRutasDemandadasServiceImpl implements ReporteRutasDemandadasService {

    static final String TITULO = "Rutas más demandadas";

    private final ReporteRutasDemandadasRepository repository;
    private final ArmadorReporteTabla armador;
    private final TextosReporte textos;
    private final Dinero dinero;

    @Override
    public ReporteTabla<FilaRutaDemandada> generar(FiltroFechas intervalo) {
        List<FilaRutaDemandada> filas = repository.consultar(intervalo.desde(), intervalo.hasta())
                .stream()
                .map(this::aFila)
                .toList();
        Map<String, Object> totales = armador.totales()
                .cantidad("boletosVendidos", armador.contar(filas, FilaRutaDemandada::boletosVendidos))
                .dinero("ingreso", armador.sumar(filas, FilaRutaDemandada::ingreso))
                .cantidad("rutas", filas.size())
                .mapa();
        return armador.tabla(TITULO, armador.filtros(intervalo, Map.of()), armador.sinAgrupar(filas), totales);
    }

    private FilaRutaDemandada aFila(RutaDemandadaConsulta c) {
        return new FilaRutaDemandada(
                c.getIdRuta(),
                textos.valor(c.getOrigen()),
                textos.valor(c.getDestino()),
                c.getDistanciaKm(),
                dinero.escalar(c.getPrecioBoleto()),
                c.getBoletosVendidos() == null ? 0 : c.getBoletosVendidos(),
                c.getViajesConVentas() == null ? 0 : c.getViajesConVentas(), dinero.escalar(c.getIngreso()),
                Boolean.TRUE.equals(c.getEliminada()));
    }
}
