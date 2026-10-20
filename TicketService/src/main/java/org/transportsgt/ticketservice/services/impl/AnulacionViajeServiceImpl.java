package org.transportsgt.ticketservice.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.ticketservice.models.EstadoCompra;
import org.transportsgt.ticketservice.repositories.CompraRepository;
import org.transportsgt.ticketservice.repositories.ViajeRefRepository;
import org.transportsgt.ticketservice.services.AnulacionViajeService;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Una sola transacción (la del consumidor del evento): el viaje eliminado, las compras anuladas, sus BoletosAnulados en
 * el outbox y la fila de {@code evento_procesado} se confirman juntos.
 */
@Service
@RequiredArgsConstructor
public class AnulacionViajeServiceImpl implements AnulacionViajeService {

    private static final EnumSet<EstadoCompra> ANULABLES = EnumSet.of(EstadoCompra.RESERVADA, EstadoCompra.CONFIRMADA);

    private final ViajeRefRepository viajeRefRepository;
    private final CompraRepository compraRepository;
    private final GestorCompras gestor;

    @Override
    @Transactional
    public int anularViaje(UUID idViaje) {
        viajeRefRepository.marcarEliminado(idViaje);
        int anuladas = 0;
        for (UUID idCompra : compraRepository.findIdsPorViajeYEstado(idViaje, ANULABLES)) {
            if (gestor.anularPorViaje(idCompra)) {
                anuladas++;
            }
        }
        return anuladas;
    }
}
