package org.transportsgt.operationalcostsservice.services;

import org.transportsgt.operationalcostsservice.config.security.UsuarioAutenticado;
import org.transportsgt.operationalcostsservice.dto.request.TarifaDepreciacionRequest;
import org.transportsgt.operationalcostsservice.dto.response.TarifaDepreciacionResponse;

import java.time.LocalDate;
import java.util.List;

/** Subdominio: tarifas de depreciación por km (historial inmutable y vigencia). */
public interface TarifaDepreciacionService {

    TarifaDepreciacionResponse tarifaVigente(LocalDate fecha);

    List<TarifaDepreciacionResponse> historialTarifas();

    TarifaDepreciacionResponse cambiarTarifa(TarifaDepreciacionRequest request, UsuarioAutenticado usuario);
}
