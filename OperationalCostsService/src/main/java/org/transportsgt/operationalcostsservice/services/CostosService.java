package org.transportsgt.operationalcostsservice.services;

import org.transportsgt.operationalcostsservice.dto.request.CategoriaActivaRequest;
import org.transportsgt.operationalcostsservice.dto.request.CategoriaRequest;
import org.transportsgt.operationalcostsservice.dto.request.GastoTallerRequest;
import org.transportsgt.operationalcostsservice.dto.request.TarifaDepreciacionRequest;
import org.transportsgt.operationalcostsservice.dto.response.CategoriaResponse;
import org.transportsgt.operationalcostsservice.dto.response.GastoTallerResponse;
import org.transportsgt.operationalcostsservice.dto.response.ResumenCostosViajeResponse;
import org.transportsgt.operationalcostsservice.dto.response.TarifaDepreciacionResponse;
import org.transportsgt.operationalcostsservice.config.security.UsuarioAutenticado;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CostosService {

    TarifaDepreciacionResponse tarifaVigente(LocalDate fecha);

    List<TarifaDepreciacionResponse> historialTarifas();

    TarifaDepreciacionResponse cambiarTarifa(TarifaDepreciacionRequest request, UsuarioAutenticado usuario);

    List<CategoriaResponse> listarCategorias(Boolean activa);

    CategoriaResponse crearCategoria(CategoriaRequest request);

    CategoriaResponse actualizarCategoria(UUID id, CategoriaRequest request);

    CategoriaResponse cambiarActivaCategoria(UUID id, CategoriaActivaRequest request);

    void eliminarCategoria(UUID id);

    GastoTallerResponse registrarGastoTaller(GastoTallerRequest request, UsuarioAutenticado usuario);

    Page<GastoTallerResponse> listarGastosTaller(UUID idBus, LocalDate desde, LocalDate hasta,
                                                 UsuarioAutenticado usuario, Pageable pageable);

    GastoTallerResponse detalleGastoTaller(UUID id, UsuarioAutenticado usuario);

    ResumenCostosViajeResponse resumenViaje(UUID idViaje, UsuarioAutenticado usuario);
}
