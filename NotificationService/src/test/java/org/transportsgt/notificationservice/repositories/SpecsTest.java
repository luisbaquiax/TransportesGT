package org.transportsgt.notificationservice.repositories;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.notificationservice.models.EstadoNotificacion;
import org.transportsgt.notificationservice.models.EstadoRecordatorio;
import org.transportsgt.notificationservice.models.Notificacion;
import org.transportsgt.notificationservice.models.RecordatorioLicencia;
import org.transportsgt.notificationservice.models.TipoNotificacion;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpecsTest {

    @Mock
    private CriteriaQuery<?> query;
    @Mock
    private CriteriaBuilder cb;
    @Mock
    private Path<Object> path;

    private int condiciones() {
        ArgumentCaptor<Predicate[]> captor = ArgumentCaptor.forClass(Predicate[].class);
        verify(cb).and(captor.capture());
        return captor.getValue().length;
    }

    @Nested
    class Bitacora {

        @Mock
        private Root<Notificacion> root;

        @Test
        void filtrar_cuandoHayTodosLosFiltros_combinaCuatroCondiciones() {
            // Arrange
            OffsetDateTime dia = OffsetDateTime.parse("2026-10-20T00:00-06:00");
            when(root.get(anyString())).thenReturn(path);

            // Act
            new NotificacionSpecs().filtrar(
                            EstadoNotificacion.FALLIDA,
                            TipoNotificacion.COMPRA_BOLETOS,
                            dia,
                            dia.plusDays(1))
                    .toPredicate(root, query, cb);

            // Assert
            assertEquals(4, condiciones());
        }

        @Test
        void filtrar_sinFiltros_noRestringe() {
            // Act
            new NotificacionSpecs().filtrar(
                            null, null, null, null)
                    .toPredicate(root, query, cb);

            // Assert
            assertEquals(0, condiciones());
        }
    }

    @Nested
    class Licencias {

        @Mock
        private Root<RecordatorioLicencia> root;

        @Test
        void filtrar_cuandoHaySucursalYEstado_filtraPorElChoferYElEstado() {
            // Arrange
            when(root.get(anyString())).thenReturn(path);
            when(path.get("idSucursal")).thenReturn(path);

            // Act
            new RecordatorioSpecs()
                    .filtrar(UUID.randomUUID(),EstadoRecordatorio.PENDIENTE)
                    .toPredicate(root, query, cb);

            // Assert
            assertEquals(2, condiciones());
        }

        @Test
        void filtrar_sinFiltros_noRestringe() {
            // Act
            new RecordatorioSpecs().filtrar(null, null).toPredicate(root, query, cb);

            // Assert
            assertEquals(0, condiciones());
        }
    }
}
