package org.transportsgt.operationalcostsservice.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.operationalcostsservice.dto.request.CategoriaActivaRequest;
import org.transportsgt.operationalcostsservice.dto.request.CategoriaRequest;
import org.transportsgt.operationalcostsservice.dto.response.CategoriaResponse;
import org.transportsgt.operationalcostsservice.exception.NoEncontradoException;
import org.transportsgt.operationalcostsservice.exception.ReglaNegocioException;
import org.transportsgt.operationalcostsservice.mappers.CostosMapper;
import org.transportsgt.operationalcostsservice.models.CategoriaGasto;
import org.transportsgt.operationalcostsservice.repositories.CategoriaGastoRepository;
import org.transportsgt.operationalcostsservice.repositories.GastoTallerLineaRepository;
import org.transportsgt.operationalcostsservice.services.CategoriaGastoService;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoriaGastoServiceImpl implements CategoriaGastoService {

    private final CategoriaGastoRepository categoriaRepository;
    private final GastoTallerLineaRepository gastoTallerLineaRepository;
    private final CostosMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<CategoriaResponse> listarCategorias(Boolean activa) {
        List<CategoriaGasto> lista = Boolean.TRUE.equals(activa)
                ? categoriaRepository.findAllByActivaTrueOrderByNombre()
                : categoriaRepository.findAllByOrderByNombre();
        return mapper.toCategoriaList(lista);
    }

    @Override
    @Transactional
    public CategoriaResponse crearCategoria(CategoriaRequest request) {
        if (categoriaRepository.existsByNombreIgnoreCase(request.nombre())) {
            throw new ReglaNegocioException("CATEGORIA_DUPLICADA", "Ya existe una categoría con ese nombre");
        }
        CategoriaGasto c = CategoriaGasto.builder()
                .nombre(request.nombre())
                .descripcion(request.descripcion())
                .activa(true)
                .build();
        return mapper.toResponse(categoriaRepository.save(c));
    }

    @Override
    @Transactional
    public CategoriaResponse actualizarCategoria(UUID id, CategoriaRequest request) {
        CategoriaGasto c = categoriaRepository.findById(id)
                .orElseThrow(() -> new NoEncontradoException("CATEGORIA_NO_ENCONTRADA",
                        "No existe la categoría " + id));
        if (categoriaRepository.existsByNombreIgnoreCaseAndIdNot(request.nombre(), id)) {
            throw new ReglaNegocioException("CATEGORIA_DUPLICADA", "Ya existe otra categoría con ese nombre");
        }
        c.setNombre(request.nombre());
        c.setDescripcion(request.descripcion());
        return mapper.toResponse(categoriaRepository.save(c));
    }

    @Override
    @Transactional
    public CategoriaResponse cambiarActivaCategoria(UUID id, CategoriaActivaRequest request) {
        CategoriaGasto c = categoriaRepository.findById(id)
                .orElseThrow(() -> new NoEncontradoException("CATEGORIA_NO_ENCONTRADA",
                        "No existe la categoría " + id));
        c.setActiva(request.activa());
        return mapper.toResponse(categoriaRepository.save(c));
    }

    @Override
    @Transactional
    public void eliminarCategoria(UUID id) {
        CategoriaGasto c = categoriaRepository.findById(id)
                .orElseThrow(() -> new NoEncontradoException("CATEGORIA_NO_ENCONTRADA",
                        "No existe la categoría " + id));
        if (gastoTallerLineaRepository.existsByCategoriaId(id)) {
            throw new ReglaNegocioException("CATEGORIA_CON_GASTOS",
                    "La categoría tiene gastos asociados; desactívela en lugar de eliminarla");
        }
        categoriaRepository.delete(c);
    }
}
