package org.transportsgt.ticketservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.transportsgt.ticketservice.models.Boleto;
import org.transportsgt.ticketservice.models.EstadoBoleto;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BoletoRepository extends JpaRepository<Boleto, UUID> {

    List<Boleto> findAllByCompraIdOrderByNumeroAsiento(UUID idCompra);

    boolean existsByCodigo(String codigo);

    /** Mapa de asientos ocupados (RESERVADO o CONFIRMADO) de un viaje. */
    @Query("SELECT b.numeroAsiento FROM Boleto b WHERE b.idViaje = :idViaje AND b.estado IN :estados ORDER BY b.numeroAsiento")
    List<Short> findAsientosOcupados(@Param("idViaje") UUID idViaje, @Param("estados") Collection<EstadoBoleto> estados);

    long countByIdViajeAndEstadoIn(UUID idViaje, Collection<EstadoBoleto> estados);

    /** GET /boletos/viajes/{idViaje}/asiento-maximo (Viajes, al cambiar de bus): 0 si no hay boletos activos. */
    @Query("""
            SELECT COALESCE(MAX(b.numeroAsiento), 0) FROM Boleto b
             WHERE b.idViaje = :idViaje
               AND b.estado IN (org.transportsgt.ticketservice.models.EstadoBoleto.RESERVADO, org.transportsgt.ticketservice.models.EstadoBoleto.CONFIRMADO)
            """)
    Short findAsientoMaximo(@Param("idViaje") UUID idViaje);

    /** Confirma o anula todos los boletos de una compra (el trigger valida la transición). */
    @Modifying
    @Query("UPDATE Boleto b SET b.estado = :estado WHERE b.compra.id = :idCompra AND b.estado <> :estado")
    int actualizarEstadoPorCompra(@Param("idCompra") UUID idCompra, @Param("estado") EstadoBoleto estado);
}
