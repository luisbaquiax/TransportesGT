package org.transportsgt.reportingservice.repositories.catalogos;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.transportsgt.reportingservice.models.RepSucursal;

import java.util.List;
import java.util.UUID;

/**
 * Listas para los filtros de los reportes. Sin sucursal = todas.
 */
public interface CatalogoRepository extends Repository<RepSucursal, UUID> {

    @Query(nativeQuery = true, value = """
            SELECT s.id AS "id", s.nombre AS "nombre", s.activa AS "activo"
              FROM rep_sucursal s
             ORDER BY s.nombre
            """)
    List<OpcionConsulta> sucursales();

    /** Rutas que salen de la sucursal, con nombre "origen → destino". */
    @Query(nativeQuery = true, value = """
            SELECT r.id AS "id", COALESCE(so.nombre, '—') || ' → ' || COALESCE(sd.nombre, '—') AS "nombre",
                   NOT r.eliminada AS "activo"
              FROM rep_ruta r
              LEFT JOIN rep_sucursal so ON so.id = r.id_sucursal_origen
              LEFT JOIN rep_sucursal sd ON sd.id = r.id_sucursal_destino
             WHERE CAST(:idSucursal AS uuid) IS NULL OR r.id_sucursal_origen = CAST(:idSucursal AS uuid)
             ORDER BY r.eliminada, "nombre"
            """)
    List<OpcionConsulta> rutas(@Param("idSucursal") UUID idSucursal);

    /** Buses de la sucursal, con nombre "placa (marca modelo)". */
    @Query(nativeQuery = true, value = """
            SELECT b.id AS "id",
                   b.placa || COALESCE(' (' || NULLIF(TRIM(COALESCE(b.marca, '') || ' ' || COALESCE(b.modelo, '')), '') || ')', '') AS "nombre",
                   b.activo AS "activo"
              FROM rep_bus b
             WHERE CAST(:idSucursal AS uuid) IS NULL OR b.id_sucursal = CAST(:idSucursal AS uuid)
             ORDER BY b.placa
            """)
    List<OpcionConsulta> buses(@Param("idSucursal") UUID idSucursal);
}
