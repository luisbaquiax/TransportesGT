package org.transportsgt.customerwalletservice.services;

import org.transportsgt.customerwalletservice.dto.response.RepublicacionResponse;

public interface RepublicacionEventosService {

    /**
     * Guarda en el outbox un PerfilClienteActualizado por cada cliente, con su versión actual, para reconstruir las
     * proyecciones de los consumidores (Boletos, Alquileres). Es idempotente: ignoran las versiones ya aplicadas.
     */
    RepublicacionResponse republicar();
}
