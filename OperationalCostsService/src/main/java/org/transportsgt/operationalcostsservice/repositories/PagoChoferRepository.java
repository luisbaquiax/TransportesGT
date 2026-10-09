package org.transportsgt.operationalcostsservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.transportsgt.operationalcostsservice.models.PagoChofer;

import java.util.Optional;
import java.util.UUID;

public interface PagoChoferRepository extends JpaRepository<PagoChofer, UUID> {

    /** Idempotencia al consumir LlegadaRegistrada ({@code UNIQUE(id_viaje)}). */
    boolean existsByIdViaje(UUID idViaje);

    Optional<PagoChofer> findByIdViaje(UUID idViaje);
}
