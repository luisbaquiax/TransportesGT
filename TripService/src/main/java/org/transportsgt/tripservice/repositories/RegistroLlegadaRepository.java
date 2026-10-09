package org.transportsgt.tripservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.transportsgt.tripservice.models.RegistroLlegada;

import java.util.Optional;
import java.util.UUID;

public interface RegistroLlegadaRepository extends JpaRepository<RegistroLlegada, UUID> {

    Optional<RegistroLlegada> findByViajeId(UUID idViaje);

    boolean existsByViajeId(UUID idViaje);
}
