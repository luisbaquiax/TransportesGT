package org.transportsgt.identityservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.transportsgt.identityservice.models.Sucursal;

import java.util.List;
import java.util.UUID;

public interface SucursalRepository extends JpaRepository<Sucursal, UUID> {

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, UUID id);

    List<Sucursal> findAllByActivaTrueOrderByNombre();

    List<Sucursal> findAllByOrderByNombre();
}
