package org.transportsgt.reportingservice.repositories.reportssucursal;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.transportsgt.reportingservice.models.RepBus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Reporte 7: buses de la sucursal. Chofer actual = el del viaje EN_TRANSITO; si no, el del próximo viaje
 * PROGRAMADO. Viajes realizados = FINALIZADO no eliminados (con llegada dentro del intervalo, si hay intervalo).
 */
public interface ReporteBusesRepository extends Repository<RepBus, UUID> {

    @Query(nativeQuery = true, value = """
            SELECT b.id AS "idBus", b.placa AS "placa", b.marca AS "marca", b.modelo AS "modelo",
                   CAST(b.capacidad_pasajeros AS integer) AS "capacidad", b.estado_operativo AS "estadoOperativo",
                   (SELECT c.nombre_completo
                      FROM rep_viaje v JOIN rep_chofer c ON c.id = v.id_chofer
                     WHERE v.id_bus = b.id AND NOT v.eliminado
                       AND (v.estado = 'EN_TRANSITO' OR (v.estado = 'PROGRAMADO' AND v.fecha_hora_salida >= :ahora))
                     ORDER BY CASE WHEN v.estado = 'EN_TRANSITO' THEN 0 ELSE 1 END, v.fecha_hora_salida
                     LIMIT 1) AS "chofer",
                   b.kilometraje_actual AS "kilometrajeActual",
                   (SELECT COUNT(*) FROM rep_viaje v
                     WHERE v.id_bus = b.id AND v.estado = 'FINALIZADO' AND NOT v.eliminado
                       AND (CAST(:desde AS date) IS NULL OR CAST(v.fecha_hora_llegada_real AT TIME ZONE 'America/Guatemala' AS date) >= CAST(:desde AS date))
                       AND (CAST(:hasta AS date) IS NULL OR CAST(v.fecha_hora_llegada_real AT TIME ZONE 'America/Guatemala' AS date) <= CAST(:hasta AS date))) AS "viajesRealizados",
                   b.activo AS "activo"
              FROM rep_bus b
             WHERE b.id_sucursal = :idSucursal
               AND (CAST(:estado AS varchar) IS NULL OR b.estado_operativo = CAST(:estado AS varchar))
             ORDER BY b.placa
            """)
    List<BusConsulta> consultar(@Param("idSucursal") UUID idSucursal, @Param("estado") String estado,
                                @Param("ahora") OffsetDateTime ahora, @Param("desde") LocalDate desde,
                                @Param("hasta") LocalDate hasta);
}
