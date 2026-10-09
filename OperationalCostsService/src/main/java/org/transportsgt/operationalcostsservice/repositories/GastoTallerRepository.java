package org.transportsgt.operationalcostsservice.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.transportsgt.operationalcostsservice.models.GastoTaller;

import java.util.Optional;
import java.util.UUID;

public interface GastoTallerRepository extends JpaRepository<GastoTaller, UUID>, JpaSpecificationExecutor<GastoTaller> {

    /** Detalle con sus líneas y categorías (también para armar GastoTallerRegistrado). */
    @EntityGraph(attributePaths = {"lineas", "lineas.categoria"})
    Optional<GastoTaller> findConLineasById(UUID id);

    Page<GastoTaller> findAllByIdSucursalOrderByFechaServicioDesc(UUID idSucursal, Pageable pageable);

    Page<GastoTaller> findAllByIdBusOrderByFechaServicioDesc(UUID idBus, Pageable pageable);
}
