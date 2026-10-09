package org.transportsgt.reportingservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.transportsgt.reportingservice.models.RepSucursal;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface RepSucursalRepository extends JpaRepository<RepSucursal, UUID> {

    /** Los reportes de ganancias muestran las sucursales en orden alfabético. */
    List<RepSucursal> findAllByOrderByNombre();

    /** Aplica SucursalCreada/Actualizada. Ignora el evento si su versión no es mayor que la guardada. */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO rep_sucursal (id, nombre, departamento, latitud, longitud, activa, version)
            VALUES (:id, :nombre, :departamento, :latitud, :longitud, :activa, :version)
            ON CONFLICT (id) DO UPDATE
               SET nombre = EXCLUDED.nombre, departamento = EXCLUDED.departamento, latitud = EXCLUDED.latitud,
                   longitud = EXCLUDED.longitud, activa = EXCLUDED.activa, version = EXCLUDED.version
             WHERE rep_sucursal.version < EXCLUDED.version
            """)
    int upsert(@Param("id") UUID id, @Param("nombre") String nombre, @Param("departamento") String departamento,
               @Param("latitud") BigDecimal latitud, @Param("longitud") BigDecimal longitud,
               @Param("activa") boolean activa, @Param("version") long version);
}
