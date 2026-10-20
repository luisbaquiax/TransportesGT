package org.transportsgt.ticketservice.repositories;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.ticketservice.models.Compra;
import org.transportsgt.ticketservice.models.EstadoCompra;
import org.transportsgt.ticketservice.models.ViajeRef;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpecsTest {

    private static final OffsetDateTime AHORA = OffsetDateTime.parse("2026-10-20T10:00-06:00");

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

    @Nested
    class ViajesALaVenta {

        @Mock
        private Root<ViajeRef> root;

        @Test
        @SuppressWarnings({"unchecked", "rawtypes"})
        void aLaVenta_cuandoHayTodosLosFiltros_combinaOchoCondiciones() {
            // Arrange
            when(root.get(anyString())).thenReturn(path);
            when(cb.lower(any())).thenReturn(minusculas);
            when(cb.like(minusculas, "%capital%")).thenReturn(predicado);
            ArgumentCaptor<Predicate[]> captor = ArgumentCaptor.forClass(Predicate[].class);

            // Act
            new ViajeRefSpecs().aLaVenta(AHORA, UUID.randomUUID(), UUID.randomUUID(), " Capital ", AHORA, AHORA.plusDays(1))
                    .toPredicate(root, query, cb);

            // Assert
            verify(cb).and(captor.capture());
            assertEquals(8, captor.getValue().length);
        }

        @Test
        @SuppressWarnings({"unchecked", "rawtypes"})
        void aLaVenta_sinFiltros_soloExigeProgramadoNoEliminadoYFuturo() {
            // Arrange
            when(root.get(anyString())).thenReturn(path);
            ArgumentCaptor<Predicate[]> captor = ArgumentCaptor.forClass(Predicate[].class);

            // Act
            new ViajeRefSpecs().aLaVenta(AHORA, null, null, " ", null, null).toPredicate(root, query, cb);

            // Assert
            verify(cb).and(captor.capture());
            assertEquals(3, captor.getValue().length);
            verify(cb, never()).like(any(), anyString());
        }
    }

    @Nested
    class ComprasDeSucursal {

        @Mock
        private Root<Compra> root;

        @Test
        void filtrar_cuandoHayViajeYEstado_combinaTresCondiciones() {
            // Arrange
            when(root.get(anyString())).thenReturn(path);
            ArgumentCaptor<Predicate[]> captor = ArgumentCaptor.forClass(Predicate[].class);

            // Act
            new CompraSpecs().filtrar(UUID.randomUUID(), UUID.randomUUID(), EstadoCompra.CONFIRMADA)
                    .toPredicate(root, query, cb);

            // Assert
            verify(cb).and(captor.capture());
            assertEquals(3, captor.getValue().length);
        }

        @Test
        void filtrar_cuandoSoloHaySucursal_usaUnaCondicion() {
            // Arrange
            UUID idSucursal = UUID.randomUUID();
            when(root.get("idSucursalIngreso")).thenReturn(path);
            ArgumentCaptor<Predicate[]> captor = ArgumentCaptor.forClass(Predicate[].class);

            // Act
            new CompraSpecs().filtrar(idSucursal, null, null).toPredicate(root, query, cb);

            // Assert
            verify(cb).and(captor.capture());
            assertEquals(1, captor.getValue().length);
            verify(cb).equal(path, idSucursal);
        }
    }
}
