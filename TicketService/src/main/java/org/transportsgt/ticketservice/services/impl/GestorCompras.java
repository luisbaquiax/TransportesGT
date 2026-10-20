package org.transportsgt.ticketservice.services.impl;

import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.ticketservice.config.BoletosProperties;
import org.transportsgt.ticketservice.eventos.EventosBoletos;
import org.transportsgt.ticketservice.exception.AsientoNoDisponibleException;
import org.transportsgt.ticketservice.exception.CompraNoEncontradaException;
import org.transportsgt.ticketservice.models.Boleto;
import org.transportsgt.ticketservice.models.Compra;
import org.transportsgt.ticketservice.models.EstadoBoleto;
import org.transportsgt.ticketservice.models.EstadoCompra;
import org.transportsgt.ticketservice.models.ViajeRef;
import org.transportsgt.ticketservice.repositories.BoletoRepository;
import org.transportsgt.ticketservice.repositories.CompraRepository;
import org.transportsgt.ticketservice.repositories.ViajeRefRepository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Única forma de cambiar el estado de una compra. Cada método es una transacción y bloquea la compra
 * ({@code SELECT ... FOR UPDATE}): la saga, el mostrador, el job y la anulación del viaje no se pisan.
 *
 * <p>Es un bean aparte (y no métodos privados de la saga) porque {@code @Transactional} solo se aplica al llamar a otro
 * bean: así la saga cobra en Cartera fuera de toda transacción, entre {@link #reservar} y {@link #confirmar}.
 */
@Component
@RequiredArgsConstructor
class GestorCompras {

    static final String MOTIVO_VIAJE_ELIMINADO = "VIAJE_ELIMINADO";
    static final String MOTIVO_RESERVA_VENCIDA = "RESERVA_VENCIDA";

    private final CompraRepository compraRepository;
    private final BoletoRepository boletoRepository;
    private final ViajeRefRepository viajeRefRepository;
    private final EventosBoletos eventos;
    private final GeneradorCodigoBoleto generadorCodigo;
    private final BoletosProperties propiedades;
    private final Clock clock;

    /**
     * Resultado de confirmar: {@code anulada = true} si el viaje se eliminó mientras se cobraba.
     */
    record Confirmacion(Compra compra, boolean anulada) {
    }

    /**
     * Tx1 de la compra en línea: compra {@code RESERVADA} y boletos {@code RESERVADO}, con vencimiento.
     *
     * @throws AsientoNoDisponibleException si un asiento ya está ocupado (revisión previa o {@code uq_asiento_activo})
     */
    @Transactional
    Compra reservar(Compra nueva, List<Short> asientos) {
        verificarLibres(nueva.getIdViaje(), asientos);
        nueva.setEstado(EstadoCompra.RESERVADA);
        nueva.setReservaExpiraEn(ahora().plus(propiedades.reserva().duracion()));
        agregarBoletos(nueva, asientos, EstadoBoleto.RESERVADO);
        return guardar(nueva);
    }

    /**
     * Venta de mostrador: reserva y confirma en una sola transacción y publica VentaBoletosRegistrada.
     */
    @Transactional
    Compra registrarConfirmada(Compra nueva, List<Short> asientos, UUID idRuta) {
        verificarLibres(nueva.getIdViaje(), asientos);
        nueva.setEstado(EstadoCompra.CONFIRMADA);
        nueva.setConfirmadoEn(ahora());
        agregarBoletos(nueva, asientos, EstadoBoleto.CONFIRMADO);
        Compra guardada = guardar(nueva);
        eventos.ventaRegistrada(guardada, idRuta);
        return guardada;
    }

    /**
     * Tx2 de la saga (y del job): la compra pasa a {@code CONFIRMADA} con su pago y se publica VentaBoletosRegistrada.
     * Si ya estaba confirmada no hace nada. Si ya estaba {@code ANULADA} (el viaje se eliminó mientras se cobraba),
     * guarda el {@code idPago} y vuelve a publicar BoletosAnulados para que Cartera reembolse.
     */
    @Transactional
    Confirmacion confirmar(UUID idCompra, UUID idPago) {
        Compra compra = bloquear(idCompra);
        if (compra.getEstado() == EstadoCompra.CONFIRMADA) {
            return new Confirmacion(compra, false);
        }
        if (compra.getEstado() == EstadoCompra.ANULADA) {
            if (compra.getIdPago() == null) {
                compra.setIdPago(idPago);
                Compra guardada = compraRepository.saveAndFlush(compra);
                eventos.boletosAnulados(guardada);
            }
            return new Confirmacion(compra, true);
        }
        compra.setEstado(EstadoCompra.CONFIRMADA);
        compra.setIdPago(idPago);
        compra.setConfirmadoEn(ahora());
        compra.getBoletos().forEach(b -> b.setEstado(EstadoBoleto.CONFIRMADO));
        Compra guardada = compraRepository.saveAndFlush(compra);
        eventos.ventaRegistrada(guardada, idRuta(guardada));
        return new Confirmacion(guardada, false);
    }

    /**
     * Libera una compra {@code RESERVADA} sin publicar evento (pago rechazado o reserva vencida: nunca hubo ingreso ni
     * cobro). Si ya no está reservada, no hace nada.
     */
    @Transactional
    Compra liberar(UUID idCompra, String motivo) {
        Compra compra = bloquear(idCompra);
        if (compra.getEstado() != EstadoCompra.RESERVADA) {
            return compra;
        }
        anular(compra, motivo);
        return compraRepository.saveAndFlush(compra);
    }

    /**
     * ViajeEliminado: anula la compra (reservada o confirmada) y publica BoletosAnulados.
     *
     * @return {@code false} si ya estaba anulada
     */
    @Transactional
    boolean anularPorViaje(UUID idCompra) {
        Compra compra = bloquear(idCompra);
        if (compra.getEstado() == EstadoCompra.ANULADA) {
            return false;
        }
        anular(compra, MOTIVO_VIAJE_ELIMINADO);
        Compra guardada = compraRepository.saveAndFlush(compra);
        eventos.boletosAnulados(guardada);
        return true;
    }

    private void verificarLibres(UUID idViaje, List<Short> asientos) {
        List<Short> ocupados = boletoRepository.findOcupadosEntre(idViaje, asientos);
        if (!ocupados.isEmpty()) {
            throw new AsientoNoDisponibleException(ocupados);
        }
    }

    private void agregarBoletos(Compra compra, List<Short> asientos, EstadoBoleto estado) {
        for (Short asiento : asientos) {
            compra.getBoletos().add(Boleto.builder()
                    .compra(compra)
                    .idViaje(compra.getIdViaje())
                    .numeroAsiento(asiento)
                    .estado(estado)
                    .codigo(generadorCodigo.generar())
                    .build());
        }
    }

    /**
     * {@code uq_asiento_activo} es la garantía real ante compras simultáneas del mismo asiento.
     */
    private Compra guardar(Compra compra) {
        try {
            return compraRepository.saveAndFlush(compra);
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains("uq_asiento_activo")) {
                throw new AsientoNoDisponibleException();
            }
            throw e;
        }
    }

    private void anular(Compra compra, String motivo) {
        compra.setEstado(EstadoCompra.ANULADA);
        compra.setAnuladoEn(ahora());
        compra.setMotivoAnulacion(motivo);
        compra.getBoletos().forEach(b -> b.setEstado(EstadoBoleto.ANULADO));
    }

    /**
     * Devuelve la compra bloqueada con sus boletos ya cargados: quien la llama la convierte a DTO fuera de la
     * transacción.
     */
    private Compra bloquear(UUID idCompra) {
        Compra compra = compraRepository.findByIdParaActualizar(idCompra).orElseThrow(CompraNoEncontradaException::new);
        Hibernate.initialize(compra.getBoletos());
        return compra;
    }

    private UUID idRuta(Compra compra) {
        return viajeRefRepository.findById(compra.getIdViaje()).map(ViajeRef::getIdRuta).orElse(null);
    }

    private OffsetDateTime ahora() {
        return OffsetDateTime.now(clock);
    }
}
