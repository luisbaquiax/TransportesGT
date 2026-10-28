package org.transportsgt.reportingservice.services.reportssucursal.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.reportingservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.reportingservice.dto.request.FiltroFechas;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportssucursal.FilaIngresoAlquiler;
import org.transportsgt.reportingservice.models.RepSucursal;
import org.transportsgt.reportingservice.repositories.reportssucursal.ReporteIngresosAlquileresRepository;
import org.transportsgt.reportingservice.services.comun.AlcanceSucursal;
import org.transportsgt.reportingservice.services.comun.ArmadorReporteTabla;
import org.transportsgt.reportingservice.services.reportssucursal.ReporteIngresosAlquileresService;
import org.transportsgt.reportingservice.utils.Fechas;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReporteIngresosAlquileresServiceImpl implements ReporteIngresosAlquileresService {

    static final String TITULO = "Ingresos por alquiler de buses";

    private final ReporteIngresosAlquileresRepository repository;
    private final AlcanceSucursal alcance;
    private final ArmadorReporteTabla armador;
    private final Fechas fechas;

    @Override
    public ReporteTabla<FilaIngresoAlquiler> generar(UsuarioAutenticado usuario, UUID idSucursal,
                                                     FiltroFechas intervalo) {
        RepSucursal sucursal = alcance.sucursalDelReporte(usuario, idSucursal);
        List<FilaIngresoAlquiler> filas = repository.consultar(sucursal.getId(), intervalo.desde(), intervalo.hasta())
                .stream()
                .map(c -> new FilaIngresoAlquiler(c.getIdAlquiler(), c.getCliente(), c.getNit(), c.getOrigen(),
                        c.getDestino(), fechas.enGuatemala(c.getFechaSalida()), fechas.enGuatemala(c.getFechaRetorno()), c.getPlaca(), c.getFechaPago(),
                        c.getPrecioTotal()))
                .toList();
        Map<String, Object> totales = armador.totales()
                .dinero("precioTotal", armador.sumar(filas, FilaIngresoAlquiler::precioTotal))
                .cantidad("alquileres", filas.size())
                .mapa();
        return armador.tabla(TITULO, armador.filtros(intervalo, Map.of("sucursal", sucursal.getNombre())),
                armador.sinAgrupar(filas), totales);
    }
}
