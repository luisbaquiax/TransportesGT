package org.transportsgt.reportingservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.transportsgt.reportingservice.models.RepChofer;

import java.time.LocalDate;
import java.util.UUID;

public interface RepChoferRepository extends JpaRepository<RepChofer, UUID>, JpaSpecificationExecutor<RepChofer> {

    /**
     * Aplica ChoferRegistrado/Actualizado. Ignora el evento si su versión no es mayor que la guardada.
     *
     * @param tipoLicencia nombre del enum {@code TipoLicencia}
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO rep_chofer (id, id_sucursal, numero_licencia, nombre_completo, tipo_licencia,
                                    fecha_vencimiento_licencia, activo, version)
            VALUES (:id, :idSucursal, :numeroLicencia, :nombreCompleto, :tipoLicencia,
                    :fechaVencimientoLicencia, :activo, :version)
            ON CONFLICT (id) DO UPDATE
               SET id_sucursal = EXCLUDED.id_sucursal, numero_licencia = EXCLUDED.numero_licencia,
                   nombre_completo = EXCLUDED.nombre_completo, tipo_licencia = EXCLUDED.tipo_licencia,
                   fecha_vencimiento_licencia = EXCLUDED.fecha_vencimiento_licencia,
                   activo = EXCLUDED.activo, version = EXCLUDED.version
             WHERE rep_chofer.version < EXCLUDED.version
            """)
    int upsert(@Param("id") UUID id, @Param("idSucursal") UUID idSucursal,
               @Param("numeroLicencia") String numeroLicencia, @Param("nombreCompleto") String nombreCompleto,
               @Param("tipoLicencia") String tipoLicencia,
               @Param("fechaVencimientoLicencia") LocalDate fechaVencimientoLicencia,
               @Param("activo") boolean activo, @Param("version") long version);
}
