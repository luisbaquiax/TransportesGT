package org.transportsgt.operationalcostsservice.services;

import org.transportsgt.operationalcostsservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.operationalcostsservice.dto.response.ResumenCostosViajeResponse;

import java.util.UUID;

/** Subdominio: lectura del resumen de costos generados para un viaje. */
public interface ResumenViajeService {

    ResumenCostosViajeResponse resumenViaje(UUID idViaje, UsuarioAutenticado usuario);
}
