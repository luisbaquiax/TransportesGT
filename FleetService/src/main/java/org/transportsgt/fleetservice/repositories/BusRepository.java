package org.transportsgt.fleetservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.transportsgt.eventos.payload.EstadoOperativo;
import org.transportsgt.fleetservice.models.Bus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusRepository extends JpaRepository<Bus, UUID>, JpaSpecificationExecutor<Bus> {

    Optional<Bus> findByIdAndIdSucursal(UUID id, UUID idSucursal);

    boolean existsByPlacaIgnoreCase(String placa);

    boolean existsByPlacaIgnoreCaseAndIdNot(String placa, UUID id);

    List<Bus> findAllByIdSucursalOrderByPlaca(UUID idSucursal);

    List<Bus> findAllByIdSucursalAndEstadoOperativoOrderByPlaca(UUID idSucursal, EstadoOperativo estado);

    /** Republicación de eventos. */
    List<Bus> findAllByOrderByCreadoEn();

    /**
     * SalidaRegistrada: el bus pasa a EN_TRANSITO. Cambio automático: no incrementa {@code version}
     * ni publica BusActualizado.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE bus
               SET estado_operativo = 'EN_TRANSITO',
                   kilometraje_actual = GREATEST(kilometraje_actual, :kilometraje),
                   actualizado_en = now()
             WHERE id = :id
            """)
    int registrarSalida(@Param("id") UUID id, @Param("kilometraje") BigDecimal kilometrajeInicial);

    /**
     * LlegadaRegistrada: el kilometraje nunca baja y el bus vuelve a DISPONIBLE solo si seguía EN_TRANSITO
     * (no pisa un EN_TALLER que haya puesto el administrador).
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE bus
               SET kilometraje_actual = GREATEST(kilometraje_actual, :kilometraje),
                   estado_operativo = CASE WHEN estado_operativo = 'EN_TRANSITO' THEN 'DISPONIBLE'
                                           ELSE estado_operativo END,
                   actualizado_en = now()
             WHERE id = :id
            """)
    int registrarLlegada(@Param("id") UUID id, @Param("kilometraje") BigDecimal kilometrajeFinal);
}
