package org.transportsgt.customerwalletservice.repositories;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.customerwalletservice.models.MovimientoCartera;
import org.transportsgt.customerwalletservice.models.TipoMovimiento;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovimientoCarteraSpecsTest {

    @Mock
    private Root<MovimientoCartera> root;
    @Mock
    private CriteriaQuery<?> query;
    @Mock
    private CriteriaBuilder cb;
    @Mock
    private Path<Object> path;
    @Mock
    private Predicate predicado;

    private UUID idCartera;
    private LocalDate fecha;

    @BeforeEach
    void setUp() {
        idCartera = UUID.randomUUID();
        fecha = LocalDate.parse("2026-10-20");
    }

    @Nested
    class Filtrar {

        @Test
        @SuppressWarnings({"unchecked", "rawtypes"})
        void filtrar_cuandoHayTodosLosFiltros_combinaCuatroCondiciones() {
            // Arrange
            when(root.get(anyString())).thenReturn(path);
            when(path.get("id")).thenReturn(path);
            when(cb.equal(any(), any(Object.class))).thenReturn(predicado);
            when(cb.greaterThanOrEqualTo(any(Path.class), any(LocalDate.class))).thenReturn(predicado);
            when(cb.lessThanOrEqualTo(any(Path.class), any(LocalDate.class))).thenReturn(predicado);
            ArgumentCaptor<Predicate[]> captor = ArgumentCaptor.forClass(Predicate[].class);

            // Act
            new MovimientoCarteraSpecs().filtrar(idCartera, TipoMovimiento.PAGO, fecha, fecha).toPredicate(root, query, cb);

            // Assert
            verify(cb).and(captor.capture());
            assertEquals(4, captor.getValue().length);
        }

        @Test
        @SuppressWarnings({"unchecked", "rawtypes"})
        void filtrar_cuandoSoloHayCartera_usaUnaCondicion() {
            // Arrange
            when(root.get("cartera")).thenReturn(path);
            when(path.get("id")).thenReturn(path);
            when(cb.equal(path, idCartera)).thenReturn(predicado);
            ArgumentCaptor<Predicate[]> captor = ArgumentCaptor.forClass(Predicate[].class);

            // Act
            new MovimientoCarteraSpecs().filtrar(idCartera, null, null, null).toPredicate(root, query, cb);

            // Assert
            verify(cb).and(captor.capture());
            assertEquals(1, captor.getValue().length);
            verify(cb, never()).greaterThanOrEqualTo(any(Path.class), any(LocalDate.class));
        }
    }
}
