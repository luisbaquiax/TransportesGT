package org.transportsgt.reportingservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.transportsgt.reportingservice.models.RepRuta;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface RepRutaRepository extends JpaRepository<RepRuta, UUID> {

    /** Mapa de rutas: rutas vigentes con origen en la sucursal elegida. */
    List<RepRuta> findAllByIdSucursalOrigenAndEliminadaFalse(UUID idSucursalOrigen);

    /** Aplica RutaCreada/Actualizada. Ignora el evento si su versión no es mayor que la guardada. */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO rep_ruta (id, id_sucursal_origen, id_sucursal_destino, distancia_km, precio_boleto, version)
            VALUES (:id, :idSucursalOrigen, :idSucursalDestino, :distanciaKm, :precioBoleto, :version)
            ON CONFLICT (id) DO UPDATE
               SET id_sucursal_origen = EXCLUDED.id_sucursal_origen, id_sucursal_destino = EXCLUDED.id_sucursal_destino,
                   distancia_km = EXCLUDED.distancia_km, precio_boleto = EXCLUDED.precio_boleto,
                   version = EXCLUDED.version
             WHERE rep_ruta.version < EXCLUDED.version
            """)
    int upsert(@Param("id") UUID id, @Param("idSucursalOrigen") UUID idSucursalOrigen,
               @Param("idSucursalDestino") UUID idSucursalDestino, @Param("distanciaKm") BigDecimal distanciaKm,
               @Param("precioBoleto") BigDecimal precioBoleto, @Param("version") long version);

    /** RutaEliminada: se conserva para los reportes históricos. */
    @Modifying
    @Query("UPDATE RepRuta r SET r.eliminada = true WHERE r.id = :id")
    int marcarEliminada(@Param("id") UUID id);
}
