package org.transportsgt.tripservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.transportsgt.tripservice.models.BusRef;

import java.util.List;
import java.util.UUID;

public interface BusRefRepository extends JpaRepository<BusRef, UUID> {

    /** Buses que se pueden asignar en una sucursal (activos y fuera de taller). */
    @Query("""
            SELECT b FROM BusRef b
             WHERE b.idSucursal = :idSucursal AND b.activo = true
               AND b.estadoOperativo NOT IN (org.transportsgt.eventos.payload.EstadoOperativo.EN_TALLER,
                                             org.transportsgt.eventos.payload.EstadoOperativo.FUERA_DE_SERVICIO)
             ORDER BY b.placa
            """)
    List<BusRef> findAsignablesPorSucursal(@Param("idSucursal") UUID idSucursal);

    /**
     * Aplica BusRegistrado/Actualizado. Ignora el evento si su versión no es mayor que la guardada.
     *
     * @param estadoOperativo nombre del enum {@code EstadoOperativo}
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO bus_ref (id, id_sucursal, placa, capacidad_pasajeros, estado_operativo, activo, version)
            VALUES (:id, :idSucursal, :placa, :capacidad, :estadoOperativo, :activo, :version)
            ON CONFLICT (id) DO UPDATE
               SET id_sucursal = EXCLUDED.id_sucursal, placa = EXCLUDED.placa,
                   capacidad_pasajeros = EXCLUDED.capacidad_pasajeros, estado_operativo = EXCLUDED.estado_operativo,
                   activo = EXCLUDED.activo, version = EXCLUDED.version
             WHERE bus_ref.version < EXCLUDED.version
            """)
    int upsert(@Param("id") UUID id, @Param("idSucursal") UUID idSucursal, @Param("placa") String placa,
               @Param("capacidad") short capacidadPasajeros, @Param("estadoOperativo") String estadoOperativo,
               @Param("activo") boolean activo, @Param("version") long version);
}
