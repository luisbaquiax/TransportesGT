package org.transportsgt.fleetservice.repositories;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.payload.EstadoOperativo;
import org.transportsgt.fleetservice.models.Bus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Filtros opcionales del listado de buses.
 */
@Component
public class BusSpecs {

    public Specification<Bus> filtrar(UUID idSucursal, EstadoOperativo estadoOperativo,
                                             Boolean activo, String texto) {
        return (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (idSucursal != null) {
                condiciones.add(cb.equal(root.get("idSucursal"), idSucursal));
            }
            if (estadoOperativo != null) {
                condiciones.add(cb.equal(root.get("estadoOperativo"), estadoOperativo));
            }
            if (activo != null) {
                condiciones.add(cb.equal(root.get("activo"), activo));
            }
            if (texto != null && !texto.isBlank()) {
                String patron = "%" + texto.trim().toLowerCase(Locale.ROOT) + "%";
                Predicate buscaPlaca = cb.like(cb.lower(root.get("placa")), patron);
                Predicate buscaMarca = cb.like(cb.lower(root.get("marca")), patron);
                Predicate buscaModelo = cb.like(cb.lower(root.get("modelo")), patron);
                condiciones.add(cb.or(buscaPlaca, buscaMarca, buscaModelo));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
    }
}
