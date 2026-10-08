package org.transportsgt.identityservice.services;

import org.transportsgt.identityservice.dto.response.RepublicacionResponse;

public interface RepublicacionEventosService {

    /**
     * Guarda en el outbox SucursalCreada y UsuarioCreado de todo lo existente (p. ej. datos del seed, que no
     * generan eventos), con la version actual: los consumidores hacen upsert e ignoran versiones ya aplicadas.
     */
    RepublicacionResponse republicar();
}
