package org.transportsgt.operationalcostsservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.transportsgt.operationalcostsservice.models.BusRef;

import java.util.List;
import java.util.UUID;

public interface BusRefRepository extends JpaRepository<BusRef, UUID> {

    /** Buses de la sucursal del administrador (para registrar gastos de taller). */
    List<BusRef> findAllByIdSucursalOrderByPlaca(UUID idSucursal);

    /**
     * Aplica BusRegistrado/Actualizado. Ignora el evento si su versión no es mayor que la guardada.
     * Si la sucursal aún no llegó, falla por la FK y el consumidor reintenta.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO bus_ref (id, id_sucursal, placa, activo, version)
            VALUES (:id, :idSucursal, :placa, :activo, :version)
            ON CONFLICT (id) DO UPDATE
               SET id_sucursal = EXCLUDED.id_sucursal, placa = EXCLUDED.placa,
                   activo = EXCLUDED.activo, version = EXCLUDED.version
             WHERE bus_ref.version < EXCLUDED.version
            """)
    int upsert(@Param("id") UUID id, @Param("idSucursal") UUID idSucursal, @Param("placa") String placa,
               @Param("activo") boolean activo, @Param("version") long version);
}
