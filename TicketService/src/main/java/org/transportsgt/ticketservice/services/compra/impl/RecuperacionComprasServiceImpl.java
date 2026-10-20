package org.transportsgt.ticketservice.services.compra.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.transportsgt.ticketservice.clients.PasarelaPago;
import org.transportsgt.ticketservice.config.BoletosProperties;
import org.transportsgt.ticketservice.exception.CarteraNoDisponibleException;
import org.transportsgt.ticketservice.models.Compra;
import org.transportsgt.ticketservice.models.EstadoCompra;
import org.transportsgt.ticketservice.repositories.CompraRepository;
import org.transportsgt.ticketservice.services.compra.RecuperacionComprasService;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * No es transaccional: consulta a Cartera fuera de toda transacción y delega los cambios en {@link GestorCompras}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecuperacionComprasServiceImpl implements RecuperacionComprasService {

    private final CompraRepository compraRepository;
    private final PasarelaPago pasarelaPago;
    private final GestorCompras gestor;
    private final BoletosProperties propiedades;
    private final Clock clock;

    /**
     * Una compra tiene la antigüedad mínima cuando su reserva vence dentro de (duración − antigüedad): la reserva se
     * calcula con el {@code Clock} al crearla.
     */
    @Override
    public List<UUID> pendientes() {
        Duration margen = propiedades.reserva().duracion().minus(propiedades.recuperacion().antiguedad());
        return compraRepository.findIdsReservadasQueVencenAntesDe(OffsetDateTime.now(clock).plus(margen));
    }

    @Override
    public Resultado recuperar(UUID idCompra) {
        Optional<Compra> compra = compraRepository.findById(idCompra);
        if (compra.isEmpty() || compra.get().getEstado() != EstadoCompra.RESERVADA) {
            // La saga o un ViajeEliminado la resolvieron mientras tanto
            return Resultado.PENDIENTE;
        }
        Optional<UUID> idPago;
        try {
            idPago = pasarelaPago.consultarPago(idCompra);
        } catch (CarteraNoDisponibleException e) {
            log.warn("Compra {} sin verificar: {}", idCompra, e.getMessage());
            return Resultado.PENDIENTE;
        }
        if (idPago.isPresent()) {
            gestor.confirmar(idCompra, idPago.get());
            return Resultado.CONFIRMADA;
        }
        if (!compra.get().getReservaExpiraEn().isAfter(OffsetDateTime.now(clock))) {
            gestor.liberar(idCompra, GestorCompras.MOTIVO_RESERVA_VENCIDA);
            return Resultado.LIBERADA;
        }
        return Resultado.PENDIENTE;
    }
}
