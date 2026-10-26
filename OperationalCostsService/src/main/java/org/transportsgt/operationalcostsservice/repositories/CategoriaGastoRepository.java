package org.transportsgt.operationalcostsservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.transportsgt.operationalcostsservice.models.CategoriaGasto;

import java.util.List;
import java.util.UUID;

public interface CategoriaGastoRepository extends JpaRepository<CategoriaGasto, UUID> {

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, UUID id);

    /** Las que se ofrecen al registrar un gasto. */
    List<CategoriaGasto> findAllByActivaTrueOrderByNombre();

    List<CategoriaGasto> findAllByOrderByNombre();
}
