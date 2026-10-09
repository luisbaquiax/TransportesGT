package org.transportsgt.operationalcostsservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.transportsgt.operationalcostsservice.models.GastoTallerLinea;

import java.util.UUID;

public interface GastoTallerLineaRepository extends JpaRepository<GastoTallerLinea, UUID> {

    /** Una categoría con gastos no se elimina, solo se desactiva (la FK RESTRICT lo refuerza). */
    boolean existsByCategoriaId(UUID idCategoria);
}
