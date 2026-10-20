package org.transportsgt.notificationservice.repositories;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.transportsgt.notificationservice.models.EstadoNotificacion;
import org.transportsgt.notificationservice.models.Notificacion;
import org.transportsgt.notificationservice.models.TipoNotificacion;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Filtros opcionales de la bitácora de correos. Un filtro nulo no restringe.
 */
@Component
public class NotificacionSpecs {

    /**
     * @param desde inicio del rango de {@code creado_en}, inclusivo
     * @param hasta fin del rango, exclusivo
     */
    public Specification<Notificacion> filtrar(EstadoNotificacion estado, TipoNotificacion tipo,
                                               OffsetDateTime desde, OffsetDateTime hasta) {
        return (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (estado != null) {
                condiciones.add(cb.equal(root.get("estado"), estado));
            }
            if (tipo != null) {
                condiciones.add(cb.equal(root.get("tipo"), tipo));
            }
            if (desde != null) {
                condiciones.add(cb.greaterThanOrEqualTo(root.get("creadoEn"), desde));
            }
            if (hasta != null) {
                condiciones.add(cb.lessThan(root.get("creadoEn"), hasta));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
    }
}
