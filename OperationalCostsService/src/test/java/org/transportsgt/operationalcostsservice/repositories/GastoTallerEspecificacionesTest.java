package org.transportsgt.operationalcostsservice.repositories;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.operationalcostsservice.models.GastoTaller;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GastoTallerEspecificacionesTest {

    @Mock Root<GastoTaller> root;
    @Mock CriteriaQuery<?> query;
    @Mock CriteriaBuilder cb;
    @Mock Path<Object> path;
    @Mock Predicate predicado;

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void conTodosLosFiltrosCombinaCuatroCondiciones() {
        when(root.get(anyString())).thenReturn(path);
        when(cb.equal(any(), any(Object.class))).thenReturn(predicado);
        when(cb.greaterThanOrEqualTo(any(Path.class), any(LocalDate.class))).thenReturn(predicado);
        when(cb.lessThanOrEqualTo(any(Path.class), any(LocalDate.class))).thenReturn(predicado);
        ArgumentCaptor<Predicate[]> captor = ArgumentCaptor.forClass(Predicate[].class);
        LocalDate fecha = LocalDate.parse("2026-10-20");

        GastoTallerEspecificaciones.conFiltros(UUID.randomUUID(), fecha, fecha, UUID.randomUUID())
                .toPredicate(root, query, cb);

        verify(cb).and(captor.capture());
        assertThat(captor.getValue()).hasSize(4);
    }

    @Test
    void sinFiltrosNoAgregaCondiciones() {
        ArgumentCaptor<Predicate[]> captor = ArgumentCaptor.forClass(Predicate[].class);

        GastoTallerEspecificaciones.conFiltros(null, null, null, null).toPredicate(root, query, cb);

        verify(cb).and(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }
}
