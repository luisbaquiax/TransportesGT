package org.transportsgt.reportingservice.repositories.reportsadmin;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.transportsgt.reportingservice.models.RepGasto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Líneas de taller filtradas por fecha de servicio, sucursal y bus (reportes 5 y 12).
 */
public interface ReporteGastosTallerRepository extends Repository<RepGasto, UUID> {

    @Query(nativeQuery = true, value = """
            SELECT COALESCE(g.nombre_categoria, 'Sin categoría') AS "categoria", g.id_bus AS "idBus",
                   b.placa AS "placa", s.nombre AS "sucursal", g.fecha_gasto AS "fecha",
                   g.descripcion AS "descripcion", g.tipo_linea AS "tipoLinea", g.monto AS "monto"
              FROM rep_gasto g
              LEFT JOIN rep_bus b      ON b.id = g.id_bus
              LEFT JOIN rep_sucursal s ON s.id = g.id_sucursal
             WHERE g.tipo_gasto = 'TALLER'
               AND (CAST(:desde AS date) IS NULL OR g.fecha_gasto >= CAST(:desde AS date))
               AND (CAST(:hasta AS date) IS NULL OR g.fecha_gasto <= CAST(:hasta AS date))
               AND (CAST(:idSucursal AS uuid) IS NULL OR g.id_sucursal = CAST(:idSucursal AS uuid))
               AND (CAST(:idBus AS uuid) IS NULL OR g.id_bus = CAST(:idBus AS uuid))
             ORDER BY "categoria", b.placa, g.fecha_gasto, g.creado_en
            """)
    List<GastoTallerConsulta> consultar(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta,
                                        @Param("idSucursal") UUID idSucursal, @Param("idBus") UUID idBus);
}
