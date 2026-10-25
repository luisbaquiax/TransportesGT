package org.transportsgt.reportingservice.services.reportssucursal.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.reportingservice.config.security.UsuarioAutenticado;
import org.transportsgt.reportingservice.dto.request.FiltroFechas;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportssucursal.FilaIngresoBoleto;
import org.transportsgt.reportingservice.models.RepSucursal;
import org.transportsgt.reportingservice.repositories.RepSucursalRepository;
import org.transportsgt.reportingservice.repositories.reportssucursal.ReporteIngresosBoletosRepository;
import org.transportsgt.reportingservice.services.comun.AlcanceSucursal;
import org.transportsgt.reportingservice.services.comun.ArmadorReporteTabla;
import org.transportsgt.reportingservice.services.comun.TextosReporte;
import org.transportsgt.reportingservice.services.reportssucursal.ReporteIngresosBoletosService;
import org.transportsgt.reportingservice.utils.Fechas;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReporteIngresosBoletosServiceImpl implements ReporteIngresosBoletosService {

    static final String TITULO = "Ingresos por venta de boletos";

    private final ReporteIngresosBoletosRepository repository;
    private final AlcanceSucursal alcance;
    private final ArmadorReporteTabla armador;
    private final TextosReporte textos;
    private final RepSucursalRepository sucursalRepository;
    private final Fechas fechas;

    @Override
    public ReporteTabla<FilaIngresoBoleto> generar(UsuarioAutenticado usuario, UUID idSucursal,
                                                   FiltroFechas intervalo, UUID idRuta, UUID idBus) {
        RepSucursal sucursal = alcance.sucursalDelReporte(usuario, idSucursal);
        Map<String, String> otros = new LinkedHashMap<>();
        otros.put("sucursal", sucursal.getNombre());
        alcance.rutaDeSucursal(idRuta, sucursal.getId()).ifPresent(r -> otros.put("ruta",
                textos.trayecto(nombreSucursal(r.getIdSucursalOrigen()), nombreSucursal(r.getIdSucursalDestino()))));
        alcance.busDeSucursal(idBus, sucursal.getId()).ifPresent(b -> otros.put("bus", b.getPlaca()));
        List<FilaIngresoBoleto> filas = repository.consultar(sucursal.getId(), intervalo.desde(), intervalo.hasta(),
                        idRuta, idBus).stream()
                .map(c -> new FilaIngresoBoleto(c.getIdViaje(), textos.trayecto(c.getOrigen(), c.getDestino()),
                        fechas.enGuatemala(c.getFechaHoraSalida()), c.getPlaca(),
                        c.getBoletosVendidos() == null ? 0 : c.getBoletosVendidos(), c.getIngreso()))
                .toList();
        Map<String, Object> totales = armador.totales()
                .cantidad("boletosVendidos", armador.contar(filas, FilaIngresoBoleto::boletosVendidos))
                .dinero("ingreso", armador.sumar(filas, FilaIngresoBoleto::ingreso))
                .cantidad("viajes", filas.size())
                .mapa();
        return armador.tabla(TITULO, armador.filtros(intervalo, otros), armador.sinAgrupar(filas), totales);
    }

    private String nombreSucursal(UUID id) {
        return sucursalRepository.findById(id).map(RepSucursal::getNombre).orElse(null);
    }
}
