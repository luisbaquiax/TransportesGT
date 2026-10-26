package org.transportsgt.reportingservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.transportsgt.reportingservice.models.RepGasto;
import org.transportsgt.reportingservice.models.TipoGasto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface RepGastoRepository extends JpaRepository<RepGasto, UUID> {

    /** {@code uq_rep_gasto}: un registro por gasto, línea de taller, depreciación o pago. */
    boolean existsByTipoGastoAndIdReferencia(TipoGasto tipoGasto, UUID idReferencia);

    /**
     * Gastos de Costos (hechos). Un duplicado no hace nada.
     *
     * @param tipoGasto nombre del enum {@link TipoGasto}
     * @param tipoLinea nombre del enum {@code TipoLinea} (solo taller)
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO rep_gasto (tipo_gasto, id_referencia, id_sucursal, id_bus, id_viaje, fecha_gasto, monto,
                                   id_categoria, nombre_categoria, tipo_linea, descripcion, distancia_km, monto_por_km)
            VALUES (:tipoGasto, :idReferencia, :idSucursal, CAST(:idBus AS uuid), CAST(:idViaje AS uuid), :fechaGasto,
                    :monto, CAST(:idCategoria AS uuid), CAST(:nombreCategoria AS varchar), CAST(:tipoLinea AS varchar),
                    CAST(:descripcion AS varchar), CAST(:distanciaKm AS numeric), CAST(:montoPorKm AS numeric))
            ON CONFLICT (tipo_gasto, id_referencia) DO NOTHING
            """)
    int insertar(@Param("tipoGasto") String tipoGasto, @Param("idReferencia") UUID idReferencia,
                 @Param("idSucursal") UUID idSucursal, @Param("idBus") UUID idBus, @Param("idViaje") UUID idViaje,
                 @Param("fechaGasto") LocalDate fechaGasto, @Param("monto") BigDecimal monto,
                 @Param("idCategoria") UUID idCategoria, @Param("nombreCategoria") String nombreCategoria,
                 @Param("tipoLinea") String tipoLinea, @Param("descripcion") String descripcion,
                 @Param("distanciaKm") BigDecimal distanciaKm, @Param("montoPorKm") BigDecimal montoPorKm);
}
