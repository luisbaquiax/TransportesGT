package org.transportsgt.reportingservice.services.reportssucursal.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.payload.EstadoOperativo;
import org.transportsgt.reportingservice.config.security.UsuarioAutenticado;
import org.transportsgt.reportingservice.dto.request.FiltroFechas;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportssucursal.FilaBus;
import org.transportsgt.reportingservice.models.RepSucursal;
import org.transportsgt.reportingservice.repositories.reportssucursal.ReporteBusesRepository;
import org.transportsgt.reportingservice.services.comun.AlcanceSucursal;
import org.transportsgt.reportingservice.services.comun.ArmadorReporteTabla;
import org.transportsgt.reportingservice.services.reportssucursal.ReporteBusesService;
import org.transportsgt.reportingservice.utils.Fechas;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReporteBusesServiceImpl implements ReporteBusesService {

    static final String TITULO = "Listado general de buses";

    private final ReporteBusesRepository repository;
    private final AlcanceSucursal alcance;
    private final ArmadorReporteTabla armador;
    private final Fechas fechas;

    @Override
    public ReporteTabla<FilaBus> generar(UsuarioAutenticado usuario, UUID idSucursal, FiltroFechas intervalo,
                                         EstadoOperativo estado) {
        RepSucursal sucursal = alcance.sucursalDelReporte(usuario, idSucursal);
        List<FilaBus> filas = repository.consultar(sucursal.getId(), estado == null ? null : estado.name(),
                        fechas.ahora(), intervalo.desde(), intervalo.hasta()).stream()
                .map(c -> new FilaBus(c.getIdBus(), c.getPlaca(), c.getMarca(), c.getModelo(), c.getCapacidad(),
                        c.getEstadoOperativo(), c.getChofer(), c.getKilometrajeActual(),
                        c.getViajesRealizados() == null ? 0 : c.getViajesRealizados(),
                        Boolean.TRUE.equals(c.getActivo())))
                .toList();
        Map<String, String> otros = new LinkedHashMap<>();
        otros.put("sucursal", sucursal.getNombre());
        if (estado != null) {
            otros.put("estado", estado.name());
        }
        Map<String, Object> totales = armador.totales()
                .cantidad("buses", filas.size())
                .cantidad("activos", filas.stream().filter(FilaBus::activo).count())
                .cantidad("viajesRealizados", armador.contar(filas, FilaBus::viajesRealizados))
                .mapa();
        return armador.tabla(TITULO, armador.filtros(intervalo, otros), armador.sinAgrupar(filas), totales);
    }
}
