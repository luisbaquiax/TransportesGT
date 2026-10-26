package org.transportsgt.privaterentalservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.transportsgt.privaterentalservice.models.CotizacionAlquiler;

import java.util.List;
import java.util.UUID;

public interface CotizacionAlquilerRepository extends JpaRepository<CotizacionAlquiler, UUID> {

    List<CotizacionAlquiler> findAllByAlquilerIdOrderByCreadoEn(UUID idAlquiler);
}
