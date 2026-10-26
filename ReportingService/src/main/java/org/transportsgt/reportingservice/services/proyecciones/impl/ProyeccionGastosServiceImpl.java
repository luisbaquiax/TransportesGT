package org.transportsgt.reportingservice.services.proyecciones.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.payload.DatosDepreciacion;
import org.transportsgt.eventos.payload.DatosGastoCombustible;
import org.transportsgt.eventos.payload.DatosGastoTaller;
import org.transportsgt.eventos.payload.DatosPagoChofer;
import org.transportsgt.eventos.payload.DatosTarifaDepreciacion;
import org.transportsgt.eventos.payload.LineaTaller;
import org.transportsgt.reportingservice.models.RepViaje;
import org.transportsgt.reportingservice.models.TipoGasto;
import org.transportsgt.reportingservice.repositories.RepGastoRepository;
import org.transportsgt.reportingservice.repositories.RepTarifaDepreciacionRepository;
import org.transportsgt.reportingservice.repositories.RepViajeRepository;
import org.transportsgt.reportingservice.services.proyecciones.ProyeccionGastosService;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProyeccionGastosServiceImpl implements ProyeccionGastosService {

    private final CamposObligatorios campos;
    private final RepGastoRepository gastoRepository;
    private final RepTarifaDepreciacionRepository tarifaRepository;
    private final RepViajeRepository viajeRepository;

    @Override
    @Transactional
    public void registrarCombustible(SobreEvento<DatosGastoCombustible> evento) {
        DatosGastoCombustible d = campos.datos(evento);
        campos.exigir(evento, "idGasto", d.idGasto());
        int filas = gastoRepository.insertar(TipoGasto.COMBUSTIBLE.name(), d.idGasto(), d.idSucursal(), d.idBus(),
                d.idViaje(), d.fechaGasto(), d.monto(), null, null, null, null, null, null);
        registrarResultado(evento, d.idGasto(), filas);
    }

    @Override
    @Transactional
    public void registrarDepreciacion(SobreEvento<DatosDepreciacion> evento) {
        DatosDepreciacion d = campos.datos(evento);
        campos.exigir(evento, "idDepreciacion", d.idDepreciacion());
        int filas = gastoRepository.insertar(TipoGasto.DEPRECIACION.name(), d.idDepreciacion(), d.idSucursal(),
                d.idBus(), d.idViaje(), d.fechaCalculo(), d.monto(), null, null, null, null, d.distanciaKm(),
                d.montoPorKm());
        registrarResultado(evento, d.idDepreciacion(), filas);
    }

    @Override
    @Transactional
    public void registrarTaller(SobreEvento<DatosGastoTaller> evento) {
        DatosGastoTaller d = campos.datos(evento);
        campos.exigir(evento, "lineas", d.lineas());
        int filas = 0;
        for (LineaTaller linea : d.lineas()) {
            filas += gastoRepository.insertar(TipoGasto.TALLER.name(), linea.idLinea(), d.idSucursal(), d.idBus(),
                    null, d.fechaServicio(), linea.monto(), linea.idCategoria(), linea.nombreCategoria(),
                    linea.tipo() == null ? null : linea.tipo().name(), linea.descripcion(), null, null);
        }
        log.info("{} {} taller {}: {} de {} líneas registradas idCorrelacion={}", evento.tipoEvento(),
                evento.idEvento(), d.idGasto(), filas, d.lineas().size(), evento.idCorrelacion());
    }

    @Override
    @Transactional
    public void registrarPagoChofer(SobreEvento<DatosPagoChofer> evento) {
        DatosPagoChofer d = campos.datos(evento);
        campos.exigir(evento, "idPago", d.idPago());
        // El pago no trae el bus: se toma del viaje si ya está proyectado (columna "bus" del reporte de gastos)
        UUID idBus = viajeRepository.findById(d.idViaje()).map(RepViaje::getIdBus).orElse(null);
        int filas = gastoRepository.insertar(TipoGasto.SALARIO_CHOFER.name(), d.idPago(), d.idSucursal(), idBus,
                d.idViaje(), d.fechaPago(), d.monto(), null, null, null, null, null, null);
        registrarResultado(evento, d.idPago(), filas);
    }

    @Override
    @Transactional
    public void cambiarTarifa(SobreEvento<DatosTarifaDepreciacion> evento) {
        DatosTarifaDepreciacion d = campos.datos(evento);
        campos.exigir(evento, "vigenteDesde", d.vigenteDesde());
        tarifaRepository.upsert(d.vigenteDesde(), d.montoPorKm());
        log.info("{} {} tarifa {} vigente desde {} idCorrelacion={}", evento.tipoEvento(), evento.idEvento(),
                d.montoPorKm(), d.vigenteDesde(), evento.idCorrelacion());
    }

    private void registrarResultado(SobreEvento<?> evento, UUID idReferencia, int filas) {
        log.info("{} {} gasto {} ({}) idCorrelacion={}", evento.tipoEvento(), evento.idEvento(), idReferencia,
                filas == 0 ? "ya registrado" : "registrado", evento.idCorrelacion());
    }
}
