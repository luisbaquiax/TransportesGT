package org.transportsgt.reportingservice.repositories.reportssucursal;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.transportsgt.reportingservice.models.RepIngreso;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Reporte 9: ventas de boletos no anuladas acreditadas a la sucursal, por viaje. El filtro por bus usa
 * {@code rep_viaje.id_bus}.
 */
public interface ReporteIngresosBoletosRepository extends Repository<RepIngreso, UUID> {

    @Query(nativeQuery = true, value = """
            SELECT i.id_viaje AS "idViaje", so.nombre AS "origen", sd.nombre AS "destino",
                   v.fecha_hora_salida AS "fechaHoraSalida", b.placa AS "placa",
                   CAST(SUM(i.cantidad_boletos) AS bigint) AS "boletosVendidos", SUM(i.monto) AS "ingreso"
              FROM rep_ingreso i
              LEFT JOIN rep_viaje v     ON v.id = i.id_viaje
              LEFT JOIN rep_ruta r      ON r.id = i.id_ruta
              LEFT JOIN rep_sucursal so ON so.id = r.id_sucursal_origen
              LEFT JOIN rep_sucursal sd ON sd.id = r.id_sucursal_destino
              LEFT JOIN rep_bus b       ON b.id = v.id_bus
             WHERE i.tipo_ingreso = 'VENTA_BOLETOS' AND NOT i.anulado AND i.id_sucursal = :idSucursal
               AND (v.id IS NULL OR NOT v.eliminado)
               AND (CAST(:desde AS date) IS NULL OR i.fecha_ingreso >= CAST(:desde AS date))
               AND (CAST(:hasta AS date) IS NULL OR i.fecha_ingreso <= CAST(:hasta AS date))
               AND (CAST(:idRuta AS uuid) IS NULL OR i.id_ruta = CAST(:idRuta AS uuid))
               AND (CAST(:idBus AS uuid) IS NULL OR v.id_bus = CAST(:idBus AS uuid))
             GROUP BY i.id_viaje, so.nombre, sd.nombre, v.fecha_hora_salida, b.placa
             ORDER BY v.fecha_hora_salida NULLS LAST, i.id_viaje
            """)
    List<IngresoBoletoConsulta> consultar(@Param("idSucursal") UUID idSucursal, @Param("desde") LocalDate desde,
                                          @Param("hasta") LocalDate hasta, @Param("idRuta") UUID idRuta,
                                          @Param("idBus") UUID idBus);
}
