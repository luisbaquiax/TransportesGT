package org.transportsgt.tripservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.transportsgt.tripservice.models.RegistroSalida;

import java.util.Optional;
import java.util.UUID;

public interface RegistroSalidaRepository extends JpaRepository<RegistroSalida, UUID> {

    Optional<RegistroSalida> findByViajeId(UUID idViaje);

    /** Un viaje con salida registrada ya no se puede eliminar ni registrar de nuevo. */
    boolean existsByViajeId(UUID idViaje);
}
