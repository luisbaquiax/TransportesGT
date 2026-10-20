package org.transportsgt.ticketservice.repositories;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.transportsgt.ticketservice.models.Compra;
import org.transportsgt.ticketservice.models.EstadoCompra;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Filtros del listado de compras de una sucursal ({@code GET /boletos/compras}). Un filtro nulo no restringe.
 */
@Component
public class CompraSpecs {

    public Specification<Compra> filtrar(UUID idSucursal, UUID idViaje, EstadoCompra estado) {
        return (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            condiciones.add(cb.equal(root.get("idSucursalIngreso"), idSucursal));
            if (idViaje != null) {
                condiciones.add(cb.equal(root.get("idViaje"), idViaje));
            }
            if (estado != null) {
                condiciones.add(cb.equal(root.get("estado"), estado));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
    }
}
