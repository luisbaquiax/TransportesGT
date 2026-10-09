package org.transportsgt.notificationservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.transportsgt.notificationservice.models.ChoferLicencia;

import java.time.LocalDate;
import java.util.UUID;

public interface ChoferLicenciaRepository extends JpaRepository<ChoferLicencia, UUID> {

    /** Aplica ChoferRegistrado/Actualizado. Ignora el evento si su versión no es mayor que la guardada. */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO chofer_licencia (id_chofer, id_sucursal, nombre_completo, correo,
                                         fecha_vencimiento_licencia, activo, version)
            VALUES (:idChofer, :idSucursal, :nombreCompleto, :correo, :fechaVencimientoLicencia, :activo, :version)
            ON CONFLICT (id_chofer) DO UPDATE
               SET id_sucursal = EXCLUDED.id_sucursal, nombre_completo = EXCLUDED.nombre_completo,
                   correo = EXCLUDED.correo, fecha_vencimiento_licencia = EXCLUDED.fecha_vencimiento_licencia,
                   activo = EXCLUDED.activo, version = EXCLUDED.version
             WHERE chofer_licencia.version < EXCLUDED.version
            """)
    int upsert(@Param("idChofer") UUID idChofer, @Param("idSucursal") UUID idSucursal,
               @Param("nombreCompleto") String nombreCompleto, @Param("correo") String correo,
               @Param("fechaVencimientoLicencia") LocalDate fechaVencimientoLicencia,
               @Param("activo") boolean activo, @Param("version") long version);
}
