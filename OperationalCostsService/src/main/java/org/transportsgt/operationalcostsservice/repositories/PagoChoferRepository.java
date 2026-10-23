package org.transportsgt.operationalcostsservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.transportsgt.operationalcostsservice.models.PagoChofer;

import java.util.Optional;
import java.util.UUID;

public interface PagoChoferRepository extends JpaRepository<PagoChofer, UUID> {

    Optional<PagoChofer> findByIdViaje(UUID idViaje);
}
