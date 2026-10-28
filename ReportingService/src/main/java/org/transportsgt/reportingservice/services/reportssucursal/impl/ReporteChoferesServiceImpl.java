package org.transportsgt.reportingservice.services.reportssucursal.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.reportingservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.reportingservice.dto.request.FiltroFechas;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportssucursal.FilaChofer;
import org.transportsgt.reportingservice.models.RepSucursal;
import org.transportsgt.reportingservice.repositories.reportssucursal.ReporteChoferesRepository;
import org.transportsgt.reportingservice.services.comun.AlcanceSucursal;
import org.transportsgt.reportingservice.services.comun.ArmadorReporteTabla;
import org.transportsgt.reportingservice.services.reportssucursal.ReporteChoferesService;
import org.transportsgt.reportingservice.utils.Fechas;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReporteChoferesServiceImpl implements ReporteChoferesService {

    static final String TITULO = "Listado general de choferes";
    static final int DIAS_POR_VENCER = 30;

    private final ReporteChoferesRepository repository;
    private final AlcanceSucursal alcance;
    private final ArmadorReporteTabla armador;
    private final Fechas fechas;

    @Override
    public ReporteTabla<FilaChofer> generar(UsuarioAutenticado usuario, UUID idSucursal, FiltroFechas intervalo) {
        RepSucursal sucursal = alcance.sucursalDelReporte(usuario, idSucursal);
        LocalDate hoy = fechas.hoy();
        List<FilaChofer> filas = repository.consultar(sucursal.getId(), intervalo.desde(), intervalo.hasta()).stream()
                .map(c -> new FilaChofer(c.getIdChofer(), c.getNumeroLicencia(), c.getNombre(), c.getTipoLicencia(),
                        c.getFechaVencimiento(), estadoLicencia(c.getFechaVencimiento(), hoy),
                        Boolean.TRUE.equals(c.getActivo()),
                        c.getViajesRealizados() == null ? 0 : c.getViajesRealizados()))
                .toList();
        Map<String, Object> totales = armador.totales()
                .cantidad("choferes", filas.size())
                .cantidad("activos", filas.stream().filter(FilaChofer::activo).count())
                .cantidad("licenciasVencidas", filas.stream().filter(f -> "VENCIDA".equals(f.estadoLicencia())).count())
                .cantidad("licenciasPorVencer",
                        filas.stream().filter(f -> "POR_VENCER".equals(f.estadoLicencia())).count())
                .cantidad("viajesRealizados", armador.contar(filas, FilaChofer::viajesRealizados))
                .mapa();
        return armador.tabla(TITULO, armador.filtros(intervalo, Map.of("sucursal", sucursal.getNombre())),
                armador.sinAgrupar(filas), totales);
    }

    String estadoLicencia(LocalDate vencimiento, LocalDate hoy) {
        if (vencimiento == null) {
            return "SIN_FECHA";
        }
        if (vencimiento.isBefore(hoy)) {
            return "VENCIDA";
        }
        return vencimiento.isAfter(hoy.plusDays(DIAS_POR_VENCER)) ? "VIGENTE" : "POR_VENCER";
    }
}
