package org.transportsgt.operationalcostsservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.transportsgt.operationalcostsservice.models.GastoCombustible;

import java.util.Optional;
import java.util.UUID;

public interface GastoCombustibleRepository extends JpaRepository<GastoCombustible, UUID> {

    /** Idempotencia al consumir LlegadaRegistrada ({@code UNIQUE(id_viaje)}). */
    boolean existsByIdViaje(UUID idViaje);

    Optional<GastoCombustible> findByIdViaje(UUID idViaje);
}
