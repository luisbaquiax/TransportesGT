package org.transportsgt.operationalcostsservice.services;

import org.transportsgt.operationalcostsservice.dto.request.CategoriaActivaRequest;
import org.transportsgt.operationalcostsservice.dto.request.CategoriaRequest;
import org.transportsgt.operationalcostsservice.dto.response.CategoriaResponse;

import java.util.List;
import java.util.UUID;

/** Subdominio: catálogo de categorías de gasto. */
public interface CategoriaGastoService {

    List<CategoriaResponse> listarCategorias(Boolean activa);

    CategoriaResponse crearCategoria(CategoriaRequest request);

    CategoriaResponse actualizarCategoria(UUID id, CategoriaRequest request);

    CategoriaResponse cambiarActivaCategoria(UUID id, CategoriaActivaRequest request);

    void eliminarCategoria(UUID id);
}
