package org.transportsgt.identityservice.repositories;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.transportsgt.identityservice.models.Sucursal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Filtros opcionales del listado de sucursales.
 */
public final class SucursalSpecs {

    private SucursalSpecs() {
    }

    public static Specification<Sucursal> filtrar(Boolean activa, String nombre) {
        return (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (activa != null) {
                condiciones.add(cb.equal(root.get("activa"), activa));
            }
            if (nombre != null && !nombre.isBlank()) {
                condiciones.add(cb.like(cb.lower(root.get("nombre")),
                        "%" + nombre.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
    }
}
