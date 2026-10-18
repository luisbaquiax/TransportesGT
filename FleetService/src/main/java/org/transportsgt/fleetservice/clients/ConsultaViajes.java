package org.transportsgt.fleetservice.clients;

import java.util.UUID;

/**
 * Consulta síncrona a Viajes (plan §2.6). Abstracción para que Flota no dependa del transporte
 * HTTP concreto (DIP): en producción lo implementa {@link ViajesClient} con RestClient,
 * reintentos y circuit breaker; en pruebas se sustituye por un stub.
 */
public interface ConsultaViajes {

    /**
     * ¿El bus tiene viajes PROGRAMADO o EN_TRANSITO?
     *
     * @throws org.transportsgt.fleetservice.exception.ViajesNoDisponibleException
     *         si Viajes no responde (falla cerrada: nunca se asume que no hay viajes)
     */
    boolean hayViajesActivos(UUID idBus);
}
