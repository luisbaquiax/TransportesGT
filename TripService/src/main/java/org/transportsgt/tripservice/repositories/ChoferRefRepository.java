package org.transportsgt.tripservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.transportsgt.tripservice.models.ChoferRef;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChoferRefRepository extends JpaRepository<ChoferRef, UUID> {

    /** Autorización del chofer: compara con el idUsuario del token. */
    Optional<ChoferRef> findByIdUsuario(UUID idUsuario);

    List<ChoferRef> findAllByIdSucursalAndActivoTrueOrderByNombreCompleto(UUID idSucursal);

    /** Aplica ChoferRegistrado/Actualizado. Ignora el evento si su versión no es mayor que la guardada. */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO chofer_ref (id, id_usuario, id_sucursal, nombre_completo, salario_base_viaje,
                                    fecha_vencimiento_licencia, activo, version)
            VALUES (:id, CAST(:idUsuario AS uuid), :idSucursal, :nombreCompleto, :salarioBaseViaje,
                    :fechaVencimientoLicencia, :activo, :version)
            ON CONFLICT (id) DO UPDATE
               SET id_usuario = EXCLUDED.id_usuario, id_sucursal = EXCLUDED.id_sucursal,
                   nombre_completo = EXCLUDED.nombre_completo, salario_base_viaje = EXCLUDED.salario_base_viaje,
                   fecha_vencimiento_licencia = EXCLUDED.fecha_vencimiento_licencia,
                   activo = EXCLUDED.activo, version = EXCLUDED.version
             WHERE chofer_ref.version < EXCLUDED.version
            """)
    int upsert(@Param("id") UUID id, @Param("idUsuario") UUID idUsuario, @Param("idSucursal") UUID idSucursal,
               @Param("nombreCompleto") String nombreCompleto, @Param("salarioBaseViaje") BigDecimal salarioBaseViaje,
               @Param("fechaVencimientoLicencia") LocalDate fechaVencimientoLicencia,
               @Param("activo") boolean activo, @Param("version") long version);
}
