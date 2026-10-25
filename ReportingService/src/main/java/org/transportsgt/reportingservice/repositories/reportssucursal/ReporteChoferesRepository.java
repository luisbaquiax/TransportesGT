package org.transportsgt.reportingservice.repositories.reportssucursal;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.transportsgt.reportingservice.models.RepChofer;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Reporte 8: choferes de la sucursal con sus viajes realizados (FINALIZADO no eliminados).
 */
public interface ReporteChoferesRepository extends Repository<RepChofer, UUID> {

    @Query(nativeQuery = true, value = """
            SELECT c.id AS "idChofer", c.numero_licencia AS "numeroLicencia", c.nombre_completo AS "nombre",
                   c.tipo_licencia AS "tipoLicencia", c.fecha_vencimiento_licencia AS "fechaVencimiento",
                   c.activo AS "activo",
                   (SELECT COUNT(*) FROM rep_viaje v
                     WHERE v.id_chofer = c.id AND v.estado = 'FINALIZADO' AND NOT v.eliminado
                       AND (CAST(:desde AS date) IS NULL OR CAST(v.fecha_hora_llegada_real AT TIME ZONE 'America/Guatemala' AS date) >= CAST(:desde AS date))
                       AND (CAST(:hasta AS date) IS NULL OR CAST(v.fecha_hora_llegada_real AT TIME ZONE 'America/Guatemala' AS date) <= CAST(:hasta AS date))) AS "viajesRealizados"
              FROM rep_chofer c
             WHERE c.id_sucursal = :idSucursal
             ORDER BY c.nombre_completo
            """)
    List<ChoferConsulta> consultar(@Param("idSucursal") UUID idSucursal, @Param("desde") LocalDate desde,
                                   @Param("hasta") LocalDate hasta);
}
