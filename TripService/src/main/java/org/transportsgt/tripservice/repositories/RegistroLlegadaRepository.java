package org.transportsgt.tripservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.transportsgt.tripservice.models.RegistroLlegada;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegistroLlegadaRepository extends JpaRepository<RegistroLlegada, UUID> {

    Optional<RegistroLlegada> findByViajeId(UUID idViaje);

    /** Registros de varios viajes en una sola consulta (listados). */
    List<RegistroLlegada> findAllByViajeIdIn(Collection<UUID> idsViaje);

    boolean existsByViajeId(UUID idViaje);
}
