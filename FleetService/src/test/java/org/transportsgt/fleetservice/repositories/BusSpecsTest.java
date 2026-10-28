package org.transportsgt.fleetservice.repositories;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.payload.flota.EstadoOperativo;
import org.transportsgt.fleetservice.models.Bus;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusSpecsTest {

    @Mock
    private Root<Bus> root;
    @Mock
    private CriteriaQuery<?> query;
    @Mock
    private CriteriaBuilder cb;
    @Mock
    private Path<Object> path;
    @Mock
    private Expression<String> minusculas;
    @Mock
    private Predicate predicado;

    private final BusSpecs specs = new BusSpecs();

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void filtrar_conTodosLosFiltros_combinaCuatroCondicionesYBuscaPorPlacaMarcaYModelo() {
        // Arrange
        when(root.get(anyString())).thenReturn(path);
        when(cb.equal(any(), any(Object.class))).thenReturn(predicado);
        when(cb.lower(any(Expression.class))).thenReturn(minusculas);
        when(cb.like(minusculas, "%p900%")).thenReturn(predicado);
        when(cb.or(any(Predicate[].class))).thenReturn(predicado);
        ArgumentCaptor<Predicate[]> captor = ArgumentCaptor.forClass(Predicate[].class);

        // Act
        specs.filtrar(UUID.randomUUID(), EstadoOperativo.DISPONIBLE, true, "  P900 ").toPredicate(root, query, cb);

        // Assert
        verify(cb).and(captor.capture());
        assertEquals(4, captor.getValue().length);
        verify(root).get("placa");
        verify(root).get("marca");
        verify(root).get("modelo");
    }

    @Test
    void filtrar_sinFiltros_noAgregaCondiciones() {
        // Arrange
        ArgumentCaptor<Predicate[]> captor = ArgumentCaptor.forClass(Predicate[].class);

        // Act
        specs.filtrar(null, null, null, null).toPredicate(root, query, cb);

        // Assert
        verify(cb).and(captor.capture());
        assertEquals(0, captor.getValue().length);
    }

    @Test
    void filtrar_conTextoEnBlanco_loIgnora() {
        // Arrange
        ArgumentCaptor<Predicate[]> captor = ArgumentCaptor.forClass(Predicate[].class);

        // Act
        specs.filtrar(null, null, null, "   ").toPredicate(root, query, cb);

        // Assert
        verify(cb).and(captor.capture());
        assertEquals(0, captor.getValue().length);
        verify(cb, never()).or(any(Predicate[].class));
    }
}
