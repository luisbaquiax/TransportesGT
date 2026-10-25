package org.transportsgt.reportingservice.repositories.reportsadmin;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.transportsgt.reportingservice.models.RepIngreso;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Reporte 1: ingresos no anulados por sucursal, filtrados por fecha de pago.
 */
public interface ReporteIngresosRepository extends Repository<RepIngreso, UUID> {

    @Query(nativeQuery = true, value = """
            SELECT i.id_sucursal AS "idSucursal", s.nombre AS "sucursal", i.fecha_ingreso AS "fecha",
                   i.tipo_ingreso AS "tipo", i.id_referencia AS "referencia",
                   so.nombre AS "rutaOrigen", sd.nombre AS "rutaDestino", i.origen AS "origen", i.destino AS "destino",
                   i.nombre_cliente AS "cliente", i.nit_cliente AS "nit",
                   CAST(i.cantidad_boletos AS integer) AS "cantidadBoletos", i.canal AS "canal", i.monto AS "monto"
              FROM rep_ingreso i
              LEFT JOIN rep_sucursal s  ON s.id = i.id_sucursal
              LEFT JOIN rep_ruta r      ON r.id = i.id_ruta
              LEFT JOIN rep_sucursal so ON so.id = r.id_sucursal_origen
              LEFT JOIN rep_sucursal sd ON sd.id = r.id_sucursal_destino
             WHERE NOT i.anulado
              AND (CAST(:desde AS date) IS NULL OR i.fecha_ingreso >= CAST(:desde AS date))
              AND (CAST(:hasta AS date) IS NULL OR i.fecha_ingreso <= CAST(:hasta AS date))
              AND (CAST(:idSucursal AS uuid) IS NULL OR i.id_sucursal = CAST(:idSucursal AS uuid))
             ORDER BY s.nombre, i.id_sucursal, i.fecha_ingreso, i.creado_en
            """)
    List<IngresoConsulta> consultar(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta,
                                    @Param("idSucursal") UUID idSucursal);
}
