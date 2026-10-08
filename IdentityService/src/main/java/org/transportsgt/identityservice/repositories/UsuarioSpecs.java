package org.transportsgt.identityservice.repositories;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.identityservice.models.Usuario;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Filtros opcionales del listado de usuarios. {@code texto} busca en nombre y correo.
 */
public final class UsuarioSpecs {

    private UsuarioSpecs() {
    }

    public static Specification<Usuario> filtrar(Rol rol, UUID idSucursal, Boolean activo, String texto) {
        return (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (rol != null) {
                condiciones.add(cb.equal(root.get("rol"), rol));
            }
            if (idSucursal != null) {
                condiciones.add(cb.equal(root.get("sucursal").get("id"), idSucursal));
            }
            if (activo != null) {
                condiciones.add(cb.equal(root.get("activo"), activo));
            }
            if (texto != null && !texto.isBlank()) {
                String patron = "%" + texto.trim().toLowerCase(Locale.ROOT) + "%";
                condiciones.add(cb.or(
                        cb.like(cb.lower(root.get("nombreCompleto")), patron),
                        cb.like(cb.lower(root.get("correo")), patron)));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
    }
}
