package org.transportsgt.operationalcostsservice.repositories;

import org.springframework.data.jpa.domain.Specification;
import org.transportsgt.operationalcostsservice.models.GastoTaller;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Filtros de búsqueda de gastos de taller. Vive en la capa de persistencia para que el
 * servicio no conozca el Criteria API de JPA (DIP).
 */
public final class GastoTallerEspecificaciones {

    private GastoTallerEspecificaciones() {
    }

    /**
     * @param idSucursalSolo si no es {@code null}, restringe a gastos de esa sucursal
     *                       (SIS pasa {@code null} y ve todo; SUC pasa la suya)
     */
    public static Specification<GastoTaller> conFiltros(UUID idBus, LocalDate desde, LocalDate hasta,
                                                        UUID idSucursalSolo) {
        return (root, query, cb) -> {
            List<Predicate> pred = new ArrayList<>();
            if (idSucursalSolo != null) {
                pred.add(cb.equal(root.get("idSucursal"), idSucursalSolo));
            }
            if (idBus != null) {
                pred.add(cb.equal(root.get("idBus"), idBus));
            }
            if (desde != null) {
                pred.add(cb.greaterThanOrEqualTo(root.get("fechaServicio"), desde));
            }
            if (hasta != null) {
                pred.add(cb.lessThanOrEqualTo(root.get("fechaServicio"), hasta));
            }
            return cb.and(pred.toArray(new Predicate[0]));
        };
    }
}
