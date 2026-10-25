package org.transportsgt.reportingservice.repositories.reportsadmin;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.transportsgt.reportingservice.models.RepSucursal;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Reporte 3: suma de {@code v_resumen_diario_sucursal} en el intervalo, con todas las sucursales (también las que
 * no tienen movimientos).
 */
public interface ReporteGananciasRepository extends Repository<RepSucursal, UUID> {

    @Query(nativeQuery = true, value = """
            SELECT s.id AS "idSucursal", s.nombre AS "sucursal",
                   COALESCE(SUM(v.ingresos), 0) AS "ingresos", COALESCE(SUM(v.gastos), 0) AS "gastos",
                   COALESCE(SUM(v.ganancia), 0) AS "ganancia"
              FROM rep_sucursal s
              LEFT JOIN v_resumen_diario_sucursal v
                     ON v.id_sucursal = s.id
                    AND (CAST(:desde AS date) IS NULL OR v.fecha >= CAST(:desde AS date))
                    AND (CAST(:hasta AS date) IS NULL OR v.fecha <= CAST(:hasta AS date))
             WHERE (CAST(:idSucursal AS uuid) IS NULL OR s.id = CAST(:idSucursal AS uuid))
             GROUP BY s.id, s.nombre
             ORDER BY s.nombre
            """)
    List<GananciaConsulta> consultar(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta,
                                     @Param("idSucursal") UUID idSucursal);
}
