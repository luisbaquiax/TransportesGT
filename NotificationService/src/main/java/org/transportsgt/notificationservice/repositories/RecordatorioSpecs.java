package org.transportsgt.notificationservice.repositories;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.transportsgt.notificationservice.models.EstadoRecordatorio;
import org.transportsgt.notificationservice.models.RecordatorioLicencia;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Filtros opcionales de los avisos de licencia. Un filtro nulo no restringe.
 */
@Component
public class RecordatorioSpecs {

    public Specification<RecordatorioLicencia> filtrar(UUID idSucursal, EstadoRecordatorio estado) {
        return (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (idSucursal != null) {
                condiciones.add(cb.equal(root.get("chofer").get("idSucursal"), idSucursal));
            }
            if (estado != null) {
                condiciones.add(cb.equal(root.get("estado"), estado));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
    }
}
