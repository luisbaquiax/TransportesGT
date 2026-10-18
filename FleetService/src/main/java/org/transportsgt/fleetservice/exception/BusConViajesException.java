package org.transportsgt.fleetservice.exception;

import java.util.UUID;

/**
 * 409 BUS_CON_VIAJES: no se puede desactivar un bus con viajes programados o en tránsito
 * (lo confirma Viajes con GET /viajes/activos?idBus=).
 */
public class BusConViajesException extends ConflictoException {

    public BusConViajesException(UUID idBus) {
        super("BUS_CON_VIAJES", "El bus " + idBus + " tiene viajes programados o en tránsito; "
                + "no puede desactivarse");
    }
}
