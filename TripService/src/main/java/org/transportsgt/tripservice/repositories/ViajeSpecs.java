package org.transportsgt.tripservice.repositories;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.payload.EstadoViaje;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.tripservice.config.security.UsuarioAutenticado;
import org.transportsgt.tripservice.dto.request.Requests;
import org.transportsgt.tripservice.models.ChoferRef;
import org.transportsgt.tripservice.models.Viaje;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Filtros y visibilidad del listado de viajes, resueltos en la base de datos (usa idx_viaje_sucursal e
 * idx_viaje_ruta). Las mismas reglas que {@code AutorizacionViaje}: SIS ve todo; SUC y CAJ, su sucursal;
 * CHO, solo los viajes en que es el chofer asignado.
 */
@Component
public class ViajeSpecs {

    public Specification<Viaje> filtrar(Requests.FiltroViajes filtro, UsuarioAutenticado usuario) {
        return (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (filtro.tipoViaje() != null) {
                condiciones.add(cb.equal(root.get("tipoViaje"), filtro.tipoViaje()));
            }
            if (filtro.estado() != null) {
                condiciones.add(cb.equal(root.get("estado"), filtro.estado()));
            }
            if (filtro.idRuta() != null) {
                condiciones.add(cb.equal(root.get("ruta").get("id"), filtro.idRuta()));
            }
            if (filtro.idSucursal() != null) {
                condiciones.add(cb.equal(root.get("idSucursalOrigen"), filtro.idSucursal()));
            }
            if (filtro.desde() != null) {
                condiciones.add(cb.greaterThanOrEqualTo(root.get("fechaHoraSalida"), filtro.desde()));
            }
            if (filtro.hasta() != null) {
                condiciones.add(cb.lessThanOrEqualTo(root.get("fechaHoraSalida"), filtro.hasta()));
            }
            if (Boolean.TRUE.equals(filtro.sinAsignar())) {
                condiciones.add(cb.equal(root.get("estado"), EstadoViaje.PENDIENTE_ASIGNACION));
            }

            if (usuario.esAdminSucursal() || usuario.rol() == Rol.CAJERO) {
                condiciones.add(usuario.idSucursal() == null
                        ? cb.disjunction()
                        : cb.equal(root.get("idSucursalOrigen"), usuario.idSucursal()));
            } else if (!usuario.esAdminSistema()) {
                // Chofer: una subconsulta en vez de buscar el chofer de cada viaje
                Subquery<UUID> choferesDelUsuario = query.subquery(UUID.class);
                var chofer = choferesDelUsuario.from(ChoferRef.class);
                choferesDelUsuario.select(chofer.get("id")).where(cb.equal(chofer.get("idUsuario"), usuario.id()));
                condiciones.add(root.get("idChofer").in(choferesDelUsuario));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
    }
}
