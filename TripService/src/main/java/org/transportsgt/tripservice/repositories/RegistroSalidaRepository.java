package org.transportsgt.tripservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.transportsgt.tripservice.models.RegistroSalida;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegistroSalidaRepository extends JpaRepository<RegistroSalida, UUID> {

    Optional<RegistroSalida> findByViajeId(UUID idViaje);

    /** Registros de varios viajes en una sola consulta (listados). */
    List<RegistroSalida> findAllByViajeIdIn(Collection<UUID> idsViaje);

    /** Un viaje con salida registrada ya no se puede eliminar ni registrar de nuevo. */
    boolean existsByViajeId(UUID idViaje);
}
