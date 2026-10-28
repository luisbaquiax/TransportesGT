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
import org.transportsgt.fleetservice.models.Chofer;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChoferSpecsTest {

    @Mock
    private Root<Chofer> root;
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

    private final ChoferSpecs specs = new ChoferSpecs();

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void filtrar_conTodosLosFiltros_combinaTresCondicionesYBuscaPorNombreCorreoYLicencia() {
        // Arrange
        when(root.get(anyString())).thenReturn(path);
        when(cb.equal(any(), any(Object.class))).thenReturn(predicado);
        when(cb.lower(any(Expression.class))).thenReturn(minusculas);
        when(cb.like(minusculas, "%ana%")).thenReturn(predicado);
        when(cb.or(any(Predicate[].class))).thenReturn(predicado);
        ArgumentCaptor<Predicate[]> captor = ArgumentCaptor.forClass(Predicate[].class);

        // Act
        specs.filtrar(UUID.randomUUID(), false, " Ana ").toPredicate(root, query, cb);

        // Assert
        verify(cb).and(captor.capture());
        assertEquals(3, captor.getValue().length);
        verify(root).get("nombreCompleto");
        verify(root).get("correo");
        verify(root).get("numeroLicencia");
    }

    @Test
    void filtrar_sinFiltrosYConTextoEnBlanco_noAgregaCondiciones() {
        // Arrange
        ArgumentCaptor<Predicate[]> captor = ArgumentCaptor.forClass(Predicate[].class);

        // Act
        specs.filtrar(null, null, " ").toPredicate(root, query, cb);

        // Assert
        verify(cb).and(captor.capture());
        assertEquals(0, captor.getValue().length);
        verify(cb, never()).or(any(Predicate[].class));
    }
}
