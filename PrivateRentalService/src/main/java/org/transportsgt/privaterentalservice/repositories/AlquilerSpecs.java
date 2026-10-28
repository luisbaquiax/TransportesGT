package org.transportsgt.privaterentalservice.repositories;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.transportsgt.privaterentalservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.privaterentalservice.dto.request.FiltroAlquileres;
import org.transportsgt.privaterentalservice.models.Alquiler;
import org.transportsgt.privaterentalservice.models.EstadoAlquiler;
import org.transportsgt.privaterentalservice.services.comun.CalendarioGuatemala;

import java.util.ArrayList;
import java.util.List;

/**
 * Filtros y visibilidad de GET /alquileres en la base (usa idx_alquiler_sucursal). SIS ve todos; SUC y CAJ, los de
 * su sucursal. Las fechas son días de Guatemala: {@code hasta} incluye todo ese día.
 */
@Component
@RequiredArgsConstructor
public class AlquilerSpecs {

    private final CalendarioGuatemala calendario;

    public Specification<Alquiler> filtrar(FiltroAlquileres filtro, UsuarioAutenticado usuario) {
        return (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (filtro.estado() != null) {
                condiciones.add(cb.equal(root.get("estado"), filtro.estado()));
            }
            if (filtro.desde() != null) {
                condiciones.add(cb.greaterThanOrEqualTo(root.get("fechaHoraSalida"),
                        calendario.inicioDia(filtro.desde())));
            }
            if (filtro.hasta() != null) {
                condiciones.add(cb.lessThan(root.get("fechaHoraSalida"),
                        calendario.inicioDia(filtro.hasta().plusDays(1))));
            }
            if (Boolean.TRUE.equals(filtro.sinAsignar())) {
                // Confirmados que Viajes aún no tiene con bus: los que el administrador debe asignar
                condiciones.add(cb.equal(root.get("estado"), EstadoAlquiler.CONFIRMADO));
                condiciones.add(cb.isNull(root.get("idBus")));
            }
            if (!usuario.esAdminSistema()) {
                condiciones.add(usuario.idSucursal() == null
                        ? cb.disjunction()
                        : cb.equal(root.get("idSucursalOrigen"), usuario.idSucursal()));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
    }
}
