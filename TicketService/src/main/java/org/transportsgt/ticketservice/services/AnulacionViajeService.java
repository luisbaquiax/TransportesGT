package org.transportsgt.ticketservice.services;

import java.util.UUID;

/**
 * ViajeEliminado (plan §4.4): anula las compras del viaje y libera sus asientos.
 */
public interface AnulacionViajeService {

    /**
     * Marca el viaje como eliminado, anula cada compra {@code RESERVADA} o {@code CONFIRMADA} (también las de mostrador)
     * y publica un BoletosAnulados por compra.
     *
     * @return cuántas compras se anularon
     */
    int anularViaje(UUID idViaje);
}
