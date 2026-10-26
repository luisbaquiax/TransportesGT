package org.transportsgt.privaterentalservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.transportsgt.privaterentalservice.models.TarifaAlquiler;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TarifaAlquilerRepository extends JpaRepository<TarifaAlquiler, UUID> {

    /** Tarifa vigente a una fecha: la más reciente con {@code vigente_desde <= fecha}. */
    Optional<TarifaAlquiler> findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(LocalDate fecha);

    /** Un segundo cambio con la misma fecha reemplaza al anterior. */
    Optional<TarifaAlquiler> findByVigenteDesde(LocalDate vigenteDesde);

    List<TarifaAlquiler> findAllByOrderByVigenteDesdeDesc();
}
