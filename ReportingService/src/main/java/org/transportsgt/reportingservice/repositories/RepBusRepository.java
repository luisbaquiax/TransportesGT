package org.transportsgt.reportingservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.transportsgt.reportingservice.models.RepBus;

import java.math.BigDecimal;
import java.util.UUID;

public interface RepBusRepository extends JpaRepository<RepBus, UUID>, JpaSpecificationExecutor<RepBus> {

    /**
     * Aplica BusRegistrado/Actualizado (cambios del administrador). No toca el kilometraje, que se deriva de
     * LlegadaRegistrada.
     *
     * @param estadoOperativo nombre del enum {@code EstadoOperativo}
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO rep_bus (id, id_sucursal, placa, marca, modelo, capacidad_pasajeros, estado_operativo,
                                 activo, version)
            VALUES (:id, :idSucursal, :placa, :marca, :modelo, :capacidad, :estadoOperativo, :activo, :version)
            ON CONFLICT (id) DO UPDATE
               SET id_sucursal = EXCLUDED.id_sucursal, placa = EXCLUDED.placa, marca = EXCLUDED.marca,
                   modelo = EXCLUDED.modelo, capacidad_pasajeros = EXCLUDED.capacidad_pasajeros,
                   estado_operativo = EXCLUDED.estado_operativo, activo = EXCLUDED.activo, version = EXCLUDED.version
             WHERE rep_bus.version < EXCLUDED.version
            """)
    int upsert(@Param("id") UUID id, @Param("idSucursal") UUID idSucursal, @Param("placa") String placa,
               @Param("marca") String marca, @Param("modelo") String modelo, @Param("capacidad") short capacidad,
               @Param("estadoOperativo") String estadoOperativo, @Param("activo") boolean activo,
               @Param("version") long version);

    /** SalidaRegistrada: el bus queda EN_TRANSITO (sin cambiar {@code version}). */
    @Modifying
    @Query(nativeQuery = true, value = "UPDATE rep_bus SET estado_operativo = 'EN_TRANSITO' WHERE id = :id")
    int registrarSalida(@Param("id") UUID id);

    /** LlegadaRegistrada: el kilometraje nunca baja y el bus vuelve a DISPONIBLE si seguía EN_TRANSITO. */
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE rep_bus
               SET kilometraje_actual = GREATEST(kilometraje_actual, :kilometraje),
                   estado_operativo = CASE WHEN estado_operativo = 'EN_TRANSITO' THEN 'DISPONIBLE'
                                           ELSE estado_operativo END
             WHERE id = :id
            """)
    int registrarLlegada(@Param("id") UUID id, @Param("kilometraje") BigDecimal kilometrajeFinal);
}
