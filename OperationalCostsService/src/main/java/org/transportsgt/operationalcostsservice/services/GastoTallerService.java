package org.transportsgt.operationalcostsservice.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.transportsgt.operationalcostsservice.config.security.UsuarioAutenticado;
import org.transportsgt.operationalcostsservice.dto.request.GastoTallerRequest;
import org.transportsgt.operationalcostsservice.dto.response.GastoTallerResponse;

import java.time.LocalDate;
import java.util.UUID;

/** Subdominio: gastos de taller (registro, consulta paginada y detalle). */
public interface GastoTallerService {

    GastoTallerResponse registrarGastoTaller(GastoTallerRequest request, UsuarioAutenticado usuario);

    Page<GastoTallerResponse> listarGastosTaller(UUID idBus, LocalDate desde, LocalDate hasta,
                                                 UsuarioAutenticado usuario, Pageable pageable);

    GastoTallerResponse detalleGastoTaller(UUID id, UsuarioAutenticado usuario);
}
