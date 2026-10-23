package org.transportsgt.fleetservice.repositories;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.transportsgt.fleetservice.models.Chofer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Filtros opcionales del listado de choferes.
 */
@Component
public class ChoferSpecs {

    public Specification<Chofer> filtrar(UUID idSucursal, Boolean activo, String texto) {
        return (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (idSucursal != null) {
                condiciones.add(cb.equal(root.get("idSucursal"), idSucursal));
            }
            if (activo != null) {
                condiciones.add(cb.equal(root.get("activo"), activo));
            }
            if (texto != null && !texto.isBlank()) {
                String patron = "%" + texto.trim().toLowerCase(Locale.ROOT) + "%";
                Predicate buscaNombre = cb.like(cb.lower(root.get("nombreCompleto")), patron);
                Predicate buscaCorreo = cb.like(cb.lower(root.get("correo")), patron);
                Predicate buscaLicencia = cb.like(cb.lower(root.get("numeroLicencia")), patron);
                condiciones.add(cb.or(buscaNombre, buscaCorreo, buscaLicencia));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
    }
}
