package org.transportsgt.tripservice.repositories;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.payload.identidad.Rol;
import org.transportsgt.eventos.payload.viajes.EstadoViaje;
import org.transportsgt.eventos.payload.viajes.TipoViaje;
import org.transportsgt.tripservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.tripservice.dto.request.Requests;
import org.transportsgt.tripservice.models.ChoferRef;
import org.transportsgt.tripservice.models.Viaje;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViajeSpecsTest {

    private static final UUID SUCURSAL = UUID.randomUUID();
    private static final Requests.FiltroViajes SIN_FILTROS =
            new Requests.FiltroViajes(null, null, null, null, null, null, null);

    @Mock
    private Root<Viaje> root;
    @Mock
    private CriteriaQuery<?> query;
    @Mock
    private CriteriaBuilder cb;
    @Mock
    private Path<Object> path;
    @Mock
    private Predicate predicado;

    private final ViajeSpecs specs = new ViajeSpecs();

    private static UsuarioAutenticado usuario(Rol rol, UUID idSucursal) {
        return new UsuarioAutenticado(UUID.randomUUID(), "u@transportes.gt", rol, idSucursal, "Usuario");
    }

    private Predicate[] condiciones(Requests.FiltroViajes filtro, UsuarioAutenticado usuario) {
        ArgumentCaptor<Predicate[]> captor = ArgumentCaptor.forClass(Predicate[].class);
        specs.filtrar(filtro, usuario).toPredicate(root, query, cb);
        verify(cb).and(captor.capture());
        return captor.getValue();
    }

    @Nested
    class Filtros {

        @Test
        @SuppressWarnings({"unchecked", "rawtypes"})
        void conTodosLosFiltros_agregaUnaCondicionPorFiltro() {
            // Arrange
            when(root.get(anyString())).thenReturn(path);
            when(path.get("id")).thenReturn(path);
            when(cb.equal(any(), any(Object.class))).thenReturn(predicado);
            when(cb.greaterThanOrEqualTo(any(Path.class), any(OffsetDateTime.class))).thenReturn(predicado);
            when(cb.lessThanOrEqualTo(any(Path.class), any(OffsetDateTime.class))).thenReturn(predicado);
            OffsetDateTime ahora = OffsetDateTime.parse("2026-10-20T08:00:00-06:00");
            Requests.FiltroViajes filtro = new Requests.FiltroViajes(TipoViaje.REGULAR, EstadoViaje.PROGRAMADO,
                    UUID.randomUUID(), SUCURSAL, ahora, ahora.plusDays(1), true);

            // Act
            Predicate[] condiciones = condiciones(filtro, usuario(Rol.ADMIN_SISTEMA, null));

            // Assert
            assertEquals(7, condiciones.length);
            verify(cb).equal(path, EstadoViaje.PENDIENTE_ASIGNACION);
        }

        @Test
        void sinAsignarFalso_noFiltraPorPendientes() {
            // Arrange
            Requests.FiltroViajes filtro = new Requests.FiltroViajes(null, null, null, null, null, null, false);

            // Act
            Predicate[] condiciones = condiciones(filtro, usuario(Rol.ADMIN_SISTEMA, null));

            // Assert
            assertEquals(0, condiciones.length);
            verify(cb, never()).equal(any(), any(Object.class));
        }
    }

    @Nested
    class Visibilidad {

        @Test
        void adminSucursal_veSoloSuSucursal() {
            // Arrange
            when(root.get("idSucursalOrigen")).thenReturn(path);
            when(cb.equal(path, SUCURSAL)).thenReturn(predicado);

            // Act
            Predicate[] condiciones = condiciones(SIN_FILTROS, usuario(Rol.ADMIN_SUCURSAL, SUCURSAL));

            // Assert
            assertEquals(1, condiciones.length);
            assertEquals(predicado, condiciones[0]);
        }

        @Test
        void cajeroSinSucursal_noVeNada() {
            // Arrange
            when(cb.disjunction()).thenReturn(predicado);

            // Act
            Predicate[] condiciones = condiciones(SIN_FILTROS, usuario(Rol.CAJERO, null));

            // Assert
            assertEquals(1, condiciones.length);
            verify(cb).disjunction();
        }

        @Test
        @SuppressWarnings({"unchecked", "rawtypes"})
        void chofer_veSoloLosViajesDondeEsElChoferAsignado(@Mock Subquery<UUID> subconsulta,
                                                          @Mock Root<ChoferRef> chofer) {
            // Arrange
            UsuarioAutenticado usuario = usuario(Rol.CHOFER, SUCURSAL);
            when(query.subquery(UUID.class)).thenReturn(subconsulta);
            when(subconsulta.from(ChoferRef.class)).thenReturn(chofer);
            when(chofer.get(anyString())).thenReturn((Path) path);
            when(subconsulta.select(any())).thenReturn(subconsulta);
            when(cb.equal(path, usuario.id())).thenReturn(predicado);
            when(root.get("idChofer")).thenReturn(path);
            when(path.in(subconsulta)).thenReturn(predicado);

            // Act
            Predicate[] condiciones = condiciones(SIN_FILTROS, usuario);

            // Assert
            assertEquals(1, condiciones.length);
            verify(subconsulta).where(predicado);
        }

        @Test
        void adminSistema_noRestringe() {
            assertEquals(0, condiciones(SIN_FILTROS, usuario(Rol.ADMIN_SISTEMA, null)).length);
            verify(query, never()).subquery(any(Class.class));
        }
    }
}
