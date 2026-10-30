package org.transportsgt.reportingservice.services.reportsadmin.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.reportingservice.dto.request.FiltroFechas;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportsadmin.FilaMapaRuta;
import org.transportsgt.reportingservice.exception.SucursalRequeridaException;
import org.transportsgt.reportingservice.models.RepSucursal;
import org.transportsgt.reportingservice.repositories.reportsadmin.ReporteMapaRutasRepository;
import org.transportsgt.reportingservice.services.comun.AlcanceSucursal;
import org.transportsgt.reportingservice.services.comun.ArmadorReporteTabla;
import org.transportsgt.reportingservice.services.comun.TextosReporte;
import org.transportsgt.reportingservice.services.reportsadmin.ReporteMapaRutasService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReporteMapaRutasServiceImpl implements ReporteMapaRutasService {

    static final String TITULO = "Mapa de rutas";

    private final ReporteMapaRutasRepository repository;
    private final AlcanceSucursal alcance;
    private final ArmadorReporteTabla armador;
    private final TextosReporte textos;

    /**
     * Las rutas no tienen fecha de negocio: el intervalo solo se informa en los filtros.
     */
    @Override
    public ReporteTabla<FilaMapaRuta> generar(FiltroFechas intervalo, UUID idSucursal) {
        if (idSucursal == null) {
            throw new SucursalRequeridaException();
        }
        RepSucursal sucursal = alcance.buscarSucursal(idSucursal);
        List<FilaMapaRuta> filas = repository.consultar(idSucursal).stream()
                .map(c -> new FilaMapaRuta(c.getIdRuta(), textos.valor(c.getOrigen()), c.getLatitudOrigen(),
                        c.getLongitudOrigen(), textos.valor(c.getDestino()), c.getLatitudDestino(),
                        c.getLongitudDestino(), c.getDistanciaKm(), c.getPrecioBoleto()))
                .toList();
        return armador.tabla(TITULO + " desde " + sucursal.getNombre(),
                armador.filtros(intervalo, Map.of("sucursal", sucursal.getNombre())),
                armador.sinAgrupar(filas), armador.totales().cantidad("rutas", filas.size()).mapa());
    }
}
