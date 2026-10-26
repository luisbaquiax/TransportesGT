package org.transportsgt.fleetservice.services.admin;

import org.transportsgt.fleetservice.dto.response.RepublicacionResponse;

public interface RepublicacionEventosService {

    /**
     * Publica BusRegistrado y ChoferRegistrado de todo lo existente para poblar las proyecciones
     * de los demás servicios (los datos iniciales no generan eventos). Es idempotente: los
     * consumidores ignoran las versiones que ya aplicaron.
     */
    RepublicacionResponse republicar();
}
