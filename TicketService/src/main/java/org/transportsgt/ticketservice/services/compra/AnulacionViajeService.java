package org.transportsgt.ticketservice.services.compra;

import java.util.UUID;

public interface AnulacionViajeService {

    /**
     * Marca el viaje como eliminado, anula cada compra {@code RESERVADA} o {@code CONFIRMADA} (también las de mostrador)
     * y publica un BoletosAnulados por compra.
     *
     * @return cuántas compras se anularon
     */
    int anularViaje(UUID idViaje);
}
