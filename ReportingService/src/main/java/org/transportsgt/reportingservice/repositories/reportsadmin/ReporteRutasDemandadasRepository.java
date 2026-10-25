package org.transportsgt.reportingservice.repositories.reportsadmin;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.transportsgt.reportingservice.models.RepRuta;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Reporte 4: rutas vigentes (aunque no tengan ventas) y rutas eliminadas con ventas en el intervalo, de mayor a
 * menor por boletos vendidos (ventas no anuladas).
 */
public interface ReporteRutasDemandadasRepository extends Repository<RepRuta, UUID> {

    @Query(nativeQuery = true, value = """
            SELECT r.id AS "idRuta", so.nombre AS "origen", sd.nombre AS "destino", r.distancia_km AS "distanciaKm",
                   r.precio_boleto AS "precioBoleto", r.eliminada AS "eliminada",
                   CAST(COALESCE(SUM(i.cantidad_boletos), 0) AS bigint) AS "boletosVendidos",
                   COUNT(DISTINCT i.id_viaje) AS "viajesConVentas", COALESCE(SUM(i.monto), 0) AS "ingreso"
              FROM rep_ruta r
              LEFT JOIN rep_sucursal so ON so.id = r.id_sucursal_origen
              LEFT JOIN rep_sucursal sd ON sd.id = r.id_sucursal_destino
              LEFT JOIN rep_ingreso i
                     ON i.id_ruta = r.id AND i.tipo_ingreso = 'VENTA_BOLETOS' AND NOT i.anulado
                    AND (CAST(:desde AS date) IS NULL OR i.fecha_ingreso >= CAST(:desde AS date))
                    AND (CAST(:hasta AS date) IS NULL OR i.fecha_ingreso <= CAST(:hasta AS date))
             GROUP BY r.id, so.nombre, sd.nombre, r.distancia_km, r.precio_boleto, r.eliminada
            HAVING NOT r.eliminada OR COUNT(i.id) > 0
             ORDER BY "boletosVendidos" DESC, "ingreso" DESC, so.nombre, sd.nombre
            """)
    List<RutaDemandadaConsulta> consultar(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);
}
