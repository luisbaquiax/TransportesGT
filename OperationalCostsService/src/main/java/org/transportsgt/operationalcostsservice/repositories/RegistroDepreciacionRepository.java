package org.transportsgt.operationalcostsservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.transportsgt.operationalcostsservice.models.RegistroDepreciacion;

import java.util.Optional;
import java.util.UUID;

public interface RegistroDepreciacionRepository extends JpaRepository<RegistroDepreciacion, UUID> {

    Optional<RegistroDepreciacion> findByIdViaje(UUID idViaje);
}
