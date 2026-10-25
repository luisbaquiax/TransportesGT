package org.transportsgt.reportingservice.repositories.reportssucursal;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.transportsgt.reportingservice.models.RepBus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Reporte 11: kilómetros y depreciación acumulada (gastos DEPRECIACION) de cada bus de la sucursal.
 */
public interface ReporteDepreciacionBusesRepository extends Repository<RepBus, UUID> {

    @Query(nativeQuery = true, value = """
            SELECT b.id AS "idBus", b.placa AS "placa", COALESCE(SUM(g.distancia_km), 0) AS "kmRecorridos",
                   COALESCE(SUM(g.monto), 0) AS "depreciacionAcumulada"
              FROM rep_bus b
              LEFT JOIN rep_gasto g
                     ON g.id_bus = b.id AND g.tipo_gasto = 'DEPRECIACION'
                    AND (CAST(:desde AS date) IS NULL OR g.fecha_gasto >= CAST(:desde AS date))
                    AND (CAST(:hasta AS date) IS NULL OR g.fecha_gasto <= CAST(:hasta AS date))
             WHERE b.id_sucursal = :idSucursal
             GROUP BY b.id, b.placa
             ORDER BY b.placa
            """)
    List<DepreciacionConsulta> consultar(@Param("idSucursal") UUID idSucursal, @Param("desde") LocalDate desde,
                                         @Param("hasta") LocalDate hasta);
}
