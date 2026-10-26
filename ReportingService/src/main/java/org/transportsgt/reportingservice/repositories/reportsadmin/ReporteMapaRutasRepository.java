package org.transportsgt.reportingservice.repositories.reportsadmin;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.transportsgt.reportingservice.models.RepRuta;

import java.util.List;
import java.util.UUID;

/**
 * Reporte 6: rutas no eliminadas con origen en la sucursal, con coordenadas para Leaflet.
 */
public interface ReporteMapaRutasRepository extends Repository<RepRuta, UUID> {

    @Query(nativeQuery = true, value = """
            SELECT r.id AS "idRuta", so.nombre AS "origen", so.latitud AS "latitudOrigen",
                   so.longitud AS "longitudOrigen", sd.nombre AS "destino", sd.latitud AS "latitudDestino",
                   sd.longitud AS "longitudDestino", r.distancia_km AS "distanciaKm", r.precio_boleto AS "precioBoleto"
              FROM rep_ruta r
              LEFT JOIN rep_sucursal so ON so.id = r.id_sucursal_origen
              LEFT JOIN rep_sucursal sd ON sd.id = r.id_sucursal_destino
             WHERE NOT r.eliminada AND r.id_sucursal_origen = :idSucursal
             ORDER BY sd.nombre
            """)
    List<MapaRutaConsulta> consultar(@Param("idSucursal") UUID idSucursal);
}
