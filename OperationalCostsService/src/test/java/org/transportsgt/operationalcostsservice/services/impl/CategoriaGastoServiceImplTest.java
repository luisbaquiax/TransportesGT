package org.transportsgt.operationalcostsservice.services.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.operationalcostsservice.dto.request.CategoriaRequest;
import org.transportsgt.operationalcostsservice.exception.NoEncontradoException;
import org.transportsgt.operationalcostsservice.exception.ReglaNegocioException;
import org.transportsgt.operationalcostsservice.mappers.CostosMapper;
import org.transportsgt.operationalcostsservice.models.CategoriaGasto;
import org.transportsgt.operationalcostsservice.repositories.CategoriaGastoRepository;
import org.transportsgt.operationalcostsservice.repositories.GastoTallerLineaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoriaGastoServiceImplTest {

    @Mock CategoriaGastoRepository categoriaRepository;
    @Mock GastoTallerLineaRepository gastoTallerLineaRepository;
    @Mock CostosMapper mapper;

    private CategoriaGastoServiceImpl servicio() {
        return new CategoriaGastoServiceImpl(categoriaRepository, gastoTallerLineaRepository, mapper);
    }

    @Test
    void crearCategoriaDuplicadaLanza409() {
        when(categoriaRepository.existsByNombreIgnoreCase("Llantas")).thenReturn(true);
        assertThatThrownBy(() -> servicio().crearCategoria(new CategoriaRequest("Llantas", null)))
                .isInstanceOf(ReglaNegocioException.class)
                .satisfies(ex -> assertThat(((ReglaNegocioException) ex).getCodigo())
                        .isEqualTo("CATEGORIA_DUPLICADA"));
    }

    @Test
    void eliminarCategoriaConGastosLanza409() {
        UUID id = UUID.randomUUID();
        when(categoriaRepository.findById(id)).thenReturn(Optional.of(
                CategoriaGasto.builder().id(id).nombre("Llantas").activa(true).build()));
        when(gastoTallerLineaRepository.existsByCategoriaId(id)).thenReturn(true);

        assertThatThrownBy(() -> servicio().eliminarCategoria(id))
                .isInstanceOf(ReglaNegocioException.class)
                .satisfies(ex -> assertThat(((ReglaNegocioException) ex).getCodigo())
                        .isEqualTo("CATEGORIA_CON_GASTOS"));
        verify(categoriaRepository, never()).delete(any());
    }

    @Test
    void eliminarCategoriaSinGastos() {
        UUID id = UUID.randomUUID();
        CategoriaGasto c = CategoriaGasto.builder().id(id).nombre("Tmp").activa(true).build();
        when(categoriaRepository.findById(id)).thenReturn(Optional.of(c));
        when(gastoTallerLineaRepository.existsByCategoriaId(id)).thenReturn(false);

        servicio().eliminarCategoria(id);
        verify(categoriaRepository).delete(c);
    }

    @Test
    void listarCategoriasSoloActivas() {
        CategoriaGasto c = CategoriaGasto.builder().id(UUID.randomUUID())
                .nombre("Llantas").activa(true).build();
        when(categoriaRepository.findAllByActivaTrueOrderByNombre()).thenReturn(List.of(c));
        servicio().listarCategorias(true);
        verify(mapper).toCategoriaList(List.of(c));
    }

    @Test
    void actualizarCategoriaNoEncontrada() {
        when(categoriaRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio().actualizarCategoria(UUID.randomUUID(),
                new CategoriaRequest("x", null)))
                .isInstanceOf(NoEncontradoException.class);
    }

    @Test
    void cambiarActivaNoEncontrada() {
        when(categoriaRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio().cambiarActivaCategoria(UUID.randomUUID(),
                new org.transportsgt.operationalcostsservice.dto.request.CategoriaActivaRequest(false)))
                .isInstanceOf(NoEncontradoException.class);
    }
}
