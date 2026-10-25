package org.transportsgt.reportingservice.services.proyecciones.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.payload.Canal;
import org.transportsgt.eventos.payload.DatosAlquilerCancelado;
import org.transportsgt.eventos.payload.DatosAlquilerConfirmado;
import org.transportsgt.eventos.payload.DatosBoletosAnulados;
import org.transportsgt.eventos.payload.DatosVentaBoletos;
import org.transportsgt.reportingservice.exception.ProyeccionNoDisponibleException;
import org.transportsgt.reportingservice.models.TipoIngreso;
import org.transportsgt.reportingservice.repositories.RepIngresoRepository;
import org.transportsgt.reportingservice.services.proyecciones.ProyeccionIngresosService;
import org.transportsgt.reportingservice.utils.Fechas;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProyeccionIngresosServiceImpl implements ProyeccionIngresosService {

    private final CamposObligatorios campos;
    private final RepIngresoRepository ingresoRepository;
    private final Fechas fechas;

    @Override
    @Transactional
    public void registrarVenta(SobreEvento<DatosVentaBoletos> evento) {
        DatosVentaBoletos datos = campos.datos(evento);
        campos.exigir(evento, "idCompra", datos.idCompra());
        campos.exigir(evento, "fechaPago", datos.fechaPago());
        campos.exigir(evento, "canal", datos.canal());
        int filas = ingresoRepository.insertar(TipoIngreso.VENTA_BOLETOS.name(), datos.idCompra(),
                datos.idSucursalIngreso(), datos.fechaPago(), datos.montoTotal(), datos.idViaje(), datos.idRuta(),
                datos.asientos() == null ? 0 : datos.asientos().size(), datos.canal().name(), datos.nitComprador(),
                datos.nombreComprador(), null, null, null, null);
        registrarResultado(evento, datos.idCompra(), filas);
    }

    @Override
    @Transactional
    public void anularVenta(SobreEvento<DatosBoletosAnulados> evento) {
        UUID idCompra = campos.datos(evento).idCompra();
        campos.exigir(evento, "idCompra", idCompra);
        anular(evento, TipoIngreso.VENTA_BOLETOS, idCompra, fechas.enGuatemala(evento.ocurridoEn()));
    }

    @Override
    @Transactional
    public void registrarAlquiler(SobreEvento<DatosAlquilerConfirmado> evento) {
        DatosAlquilerConfirmado datos = campos.datos(evento);
        campos.exigir(evento, "idAlquiler", datos.idAlquiler());
        campos.exigir(evento, "fechaPago", datos.fechaPago());
        // Sin pago en cartera = cobrado en efectivo en el mostrador
        Canal canal = datos.idPago() == null ? Canal.MOSTRADOR : Canal.EN_LINEA;
        int filas = ingresoRepository.insertar(TipoIngreso.ALQUILER.name(), datos.idAlquiler(),
                datos.idSucursalOrigen(), datos.fechaPago(), datos.precioTotal(), null, null, null, canal.name(),
                datos.nit(), datos.nombreCliente(), datos.origen(), datos.destino(), datos.fechaHoraSalida(),
                datos.fechaHoraRetorno());
        registrarResultado(evento, datos.idAlquiler(), filas);
    }

    @Override
    @Transactional
    public void cancelarAlquiler(SobreEvento<DatosAlquilerCancelado> evento) {
        DatosAlquilerCancelado datos = campos.datos(evento);
        campos.exigir(evento, "idAlquiler", datos.idAlquiler());
        anular(evento, TipoIngreso.ALQUILER, datos.idAlquiler(),
                fechas.enGuatemala(datos.canceladoEn() == null ? evento.ocurridoEn() : datos.canceladoEn()));
    }

    /** La anulación se aplica sobre la fecha de pago original (cambio retroactivo del periodo). */
    private void anular(SobreEvento<?> evento, TipoIngreso tipo, UUID idReferencia, OffsetDateTime anuladoEn) {
        if (!ingresoRepository.existsByTipoIngresoAndIdReferencia(tipo, idReferencia)) {
            throw new ProyeccionNoDisponibleException("El ingreso " + tipo + " " + idReferencia + " aún no llega");
        }
        int filas = ingresoRepository.anular(tipo, idReferencia, anuladoEn);
        log.info("{} {} ingreso {} {} ({}) idCorrelacion={}", evento.tipoEvento(), evento.idEvento(), tipo,
                idReferencia, filas == 0 ? "ya estaba anulado" : "anulado", evento.idCorrelacion());
    }

    private void registrarResultado(SobreEvento<?> evento, UUID idReferencia, int filas) {
        log.info("{} {} ingreso {} ({}) idCorrelacion={}", evento.tipoEvento(), evento.idEvento(), idReferencia,
                filas == 0 ? "ya registrado" : "registrado", evento.idCorrelacion());
    }
}
