package org.transportsgt.reportingservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.transportsgt.reportingservice.models.RepTarifaDepreciacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface RepTarifaDepreciacionRepository extends JpaRepository<RepTarifaDepreciacion, LocalDate> {

    /** Tarifa configurada a una fecha (reporte de depreciación por bus). */
    Optional<RepTarifaDepreciacion> findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(LocalDate fecha);

    /** TarifaDepreciacionCambiada: un segundo cambio el mismo día reemplaza al primero. */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO rep_tarifa_depreciacion (vigente_desde, monto_por_km)
            VALUES (:vigenteDesde, :montoPorKm)
            ON CONFLICT (vigente_desde) DO UPDATE SET monto_por_km = EXCLUDED.monto_por_km
            """)
    int upsert(@Param("vigenteDesde") LocalDate vigenteDesde, @Param("montoPorKm") BigDecimal montoPorKm);
}
