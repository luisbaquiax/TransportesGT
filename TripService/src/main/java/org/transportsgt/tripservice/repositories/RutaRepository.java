package org.transportsgt.tripservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.transportsgt.tripservice.models.Ruta;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RutaRepository extends JpaRepository<Ruta, UUID>, JpaSpecificationExecutor<Ruta> {

    /** La ruta solo la gestiona el administrador de la sucursal de origen. */
    Optional<Ruta> findByIdAndIdSucursalOrigen(UUID id, UUID idSucursalOrigen);

    boolean existsByIdSucursalOrigenAndIdSucursalDestino(UUID idSucursalOrigen, UUID idSucursalDestino);

    boolean existsByIdSucursalOrigenAndIdSucursalDestinoAndIdNot(UUID idSucursalOrigen, UUID idSucursalDestino, UUID id);

    List<Ruta> findAllByIdSucursalOrigen(UUID idSucursalOrigen);

    /** Republicación de eventos. */
    List<Ruta> findAllByOrderByCreadoEn();
}
