package org.transportsgt.reportingservice.repositories.reportsadmin;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.transportsgt.reportingservice.models.RepGasto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Reporte 2: gastos por sucursal (combustible, taller, depreciación y salario), filtrados por fecha de gasto.
 * El chofer del salario sale del viaje (rep_gasto no guarda el chofer).
 */
public interface ReporteGastosRepository extends Repository<RepGasto, UUID> {

    @Query(nativeQuery = true, value = """
            SELECT g.id_sucursal AS "idSucursal", s.nombre AS "sucursal", g.fecha_gasto AS "fecha",
                   g.tipo_gasto AS "tipoGasto", g.tipo_linea AS "tipoLinea", g.id_referencia AS "idReferencia",
                   b.placa AS "placa", g.nombre_categoria AS "categoria", g.descripcion AS "descripcion",
                   c.nombre_completo AS "chofer", g.distancia_km AS "distanciaKm", g.monto_por_km AS "montoPorKm",
                   g.monto AS "monto"
              FROM rep_gasto g
              LEFT JOIN rep_sucursal s ON s.id = g.id_sucursal
              LEFT JOIN rep_bus b      ON b.id = g.id_bus
              LEFT JOIN rep_viaje v    ON v.id = g.id_viaje
              LEFT JOIN rep_chofer c   ON c.id = v.id_chofer
             WHERE (CAST(:desde AS date) IS NULL OR g.fecha_gasto >= CAST(:desde AS date))
               AND (CAST(:hasta AS date) IS NULL OR g.fecha_gasto <= CAST(:hasta AS date))
               AND (CAST(:idSucursal AS uuid) IS NULL OR g.id_sucursal = CAST(:idSucursal AS uuid))
             ORDER BY s.nombre, g.id_sucursal, g.fecha_gasto, g.creado_en
            """)
    List<GastoConsulta> consultar(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta,
                                  @Param("idSucursal") UUID idSucursal);
}
