package org.transportsgt.customerwalletservice.repositories;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.transportsgt.customerwalletservice.models.MovimientoCartera;
import org.transportsgt.customerwalletservice.models.TipoMovimiento;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Filtros opcionales del libro de movimientos de una cartera. Un filtro nulo no restringe.
 */
@Component
public class MovimientoCarteraSpecs {

    public Specification<MovimientoCartera> filtrar(UUID idCartera, TipoMovimiento tipo,
                                                   LocalDate desde, LocalDate hasta) {
        return (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            condiciones.add(cb.equal(root.get("cartera").get("id"), idCartera));
            if (tipo != null) {
                condiciones.add(cb.equal(root.get("tipo"), tipo));
            }
            if (desde != null) {
                condiciones.add(cb.greaterThanOrEqualTo(root.get("fechaMovimiento"), desde));
            }
            if (hasta != null) {
                condiciones.add(cb.lessThanOrEqualTo(root.get("fechaMovimiento"), hasta));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
    }
}
