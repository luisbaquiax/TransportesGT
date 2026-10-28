package org.transportsgt.privaterentalservice.repositories;

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
import org.transportsgt.eventos.payload.identidad.Rol;
import org.transportsgt.privaterentalservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.privaterentalservice.dto.request.FiltroAlquileres;
import org.transportsgt.privaterentalservice.models.Alquiler;
import org.transportsgt.privaterentalservice.models.EstadoAlquiler;
import org.transportsgt.privaterentalservice.services.comun.CalendarioGuatemala;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlquilerSpecsTest {

    private static final FiltroAlquileres SIN_FILTROS = new FiltroAlquileres(null, null, null, null);

    @Mock
    private CalendarioGuatemala calendario;
    @Mock
    private Root<Alquiler> root;
    @Mock
    private CriteriaQuery<?> query;
    @Mock
    private CriteriaBuilder cb;
    @Mock
    private Path<Object> path;
    @Mock
    private Predicate predicado;

    private static UsuarioAutenticado usuario(Rol rol, UUID idSucursal) {
        return new UsuarioAutenticado(UUID.randomUUID(), "u@transportes.gt", rol, idSucursal, "Usuario");
    }

    private Predicate[] condiciones(FiltroAlquileres filtro, UsuarioAutenticado usuario) {
        ArgumentCaptor<Predicate[]> captor = ArgumentCaptor.forClass(Predicate[].class);
        new AlquilerSpecs(calendario).filtrar(filtro, usuario).toPredicate(root, query, cb);
        verify(cb).and(captor.capture());
        return captor.getValue();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void conTodosLosFiltrosYSinAsignarAgregaSeisCondiciones() {
        // Arrange
        OffsetDateTime inicio = OffsetDateTime.parse("2026-10-01T00:00:00-06:00");
        when(calendario.inicioDia(any(LocalDate.class))).thenReturn(inicio);
        when(root.get(anyString())).thenReturn(path);
        when(cb.equal(any(), any(Object.class))).thenReturn(predicado);
        when(cb.greaterThanOrEqualTo(any(Path.class), any(OffsetDateTime.class))).thenReturn(predicado);
        when(cb.lessThan(any(Path.class), any(OffsetDateTime.class))).thenReturn(predicado);
        when(cb.isNull(path)).thenReturn(predicado);
        FiltroAlquileres filtro = new FiltroAlquileres(EstadoAlquiler.CONFIRMADO,
                LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"), true);

        // Act
        Predicate[] condiciones = condiciones(filtro, usuario(Rol.ADMIN_SUCURSAL, UUID.randomUUID()));

        // Assert: estado, desde, hasta, (confirmado + sin bus) y la sucursal del usuario
        assertEquals(6, condiciones.length);
        verify(calendario).inicioDia(LocalDate.parse("2026-11-01"));
    }

    @Test
    void sinAsignarFalsoYAdminSistemaNoAgreganCondiciones() {
        Predicate[] condiciones = condiciones(new FiltroAlquileres(null, null, null, false),
                usuario(Rol.ADMIN_SISTEMA, null));

        assertEquals(0, condiciones.length);
    }

    @Test
    void personalSinSucursalNoVeNada() {
        when(cb.disjunction()).thenReturn(predicado);

        Predicate[] condiciones = condiciones(SIN_FILTROS, usuario(Rol.CAJERO, null));

        assertEquals(1, condiciones.length);
        verify(cb).disjunction();
    }
}
