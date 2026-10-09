package org.transportsgt.fleetservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.transportsgt.fleetservice.models.SucursalRef;

import java.util.UUID;

public interface SucursalRefRepository extends JpaRepository<SucursalRef, UUID> {

    boolean existsByIdAndActivaTrue(UUID id);

    /**
     * Aplica SucursalCreada/Actualizada. Ignora el evento si su versión no es mayor que la guardada.
     *
     * @return 1 si se aplicó, 0 si el evento era viejo o repetido
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO sucursal_ref (id, nombre, activa, version)
            VALUES (:id, :nombre, :activa, :version)
            ON CONFLICT (id) DO UPDATE
               SET nombre = EXCLUDED.nombre, activa = EXCLUDED.activa, version = EXCLUDED.version
             WHERE sucursal_ref.version < EXCLUDED.version
            """)
    int upsert(@Param("id") UUID id, @Param("nombre") String nombre,
               @Param("activa") boolean activa, @Param("version") long version);
}
