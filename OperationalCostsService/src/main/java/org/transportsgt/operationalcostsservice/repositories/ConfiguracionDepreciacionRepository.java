package org.transportsgt.operationalcostsservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.transportsgt.operationalcostsservice.models.ConfiguracionDepreciacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConfiguracionDepreciacionRepository extends JpaRepository<ConfiguracionDepreciacion, UUID> {

    /** Tarifa vigente a una fecha (la de llegada del viaje): la más reciente con {@code vigente_desde <= fecha}. */
    Optional<ConfiguracionDepreciacion> findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(LocalDate fecha);

    List<ConfiguracionDepreciacion> findAllByOrderByVigenteDesdeDesc();

    /** Cambio de tarifa: un segundo cambio con la misma {@code vigente_desde} reemplaza al primero. */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO configuracion_depreciacion (id, monto_por_km, vigente_desde, creado_por)
            VALUES (gen_random_uuid(), :montoPorKm, :vigenteDesde, :creadoPor)
            ON CONFLICT (vigente_desde) DO UPDATE
               SET monto_por_km = EXCLUDED.monto_por_km, creado_por = EXCLUDED.creado_por, creado_en = now()
            """)
    int guardarTarifa(@Param("montoPorKm") BigDecimal montoPorKm, @Param("vigenteDesde") LocalDate vigenteDesde,
                      @Param("creadoPor") UUID creadoPor);
}
