package org.transportsgt.ticketservice.repositories;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.payload.EstadoViaje;
import org.transportsgt.ticketservice.models.ViajeRef;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Viajes a la venta: {@code PROGRAMADO}, no eliminados y con salida posterior a {@code ahora}. Filtros opcionales.
 */
@Component
public class ViajeRefSpecs {

    /**
     * @param ahora salida mínima (según el {@code Clock}): un viaje que ya salió no se vende
     * @param desde inicio del rango de salida (el día pedido, en hora de Guatemala); nulo = sin límite
     * @param hasta fin del rango de salida, exclusivo; nulo = sin límite
     */
    public Specification<ViajeRef> aLaVenta(OffsetDateTime ahora, UUID idOrigen, UUID idRuta, String destino,
                                            OffsetDateTime desde, OffsetDateTime hasta) {
        return (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            condiciones.add(cb.isFalse(root.get("eliminado")));
            condiciones.add(cb.equal(root.get("estado"), EstadoViaje.PROGRAMADO));
            condiciones.add(cb.greaterThan(root.get("fechaHoraSalida"), ahora));
            if (idOrigen != null) {
                condiciones.add(cb.equal(root.get("idSucursalOrigen"), idOrigen));
            }
            if (idRuta != null) {
                condiciones.add(cb.equal(root.get("idRuta"), idRuta));
            }
            if (destino != null && !destino.isBlank()) {
                condiciones.add(cb.like(cb.lower(root.get("nombreDestino")),
                        "%" + destino.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (desde != null) {
                condiciones.add(cb.greaterThanOrEqualTo(root.get("fechaHoraSalida"), desde));
            }
            if (hasta != null) {
                condiciones.add(cb.lessThan(root.get("fechaHoraSalida"), hasta));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
    }
}
