package org.transportsgt.fleetservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.transportsgt.fleetservice.models.Chofer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChoferRepository extends JpaRepository<Chofer, UUID>, JpaSpecificationExecutor<Chofer> {

    Optional<Chofer> findByIdAndIdSucursal(UUID id, UUID idSucursal);

    Optional<Chofer> findByIdUsuario(UUID idUsuario);

    boolean existsByNumeroLicencia(String numeroLicencia);

    boolean existsByNumeroLicenciaAndIdNot(String numeroLicencia, UUID id);

    boolean existsByIdUsuario(UUID idUsuario);

    boolean existsByIdUsuarioAndIdNot(UUID idUsuario, UUID id);

    List<Chofer> findAllByIdSucursalOrderByNombreCompleto(UUID idSucursal);

    /** Republicación de eventos. */
    List<Chofer> findAllByOrderByCreadoEn();
}
