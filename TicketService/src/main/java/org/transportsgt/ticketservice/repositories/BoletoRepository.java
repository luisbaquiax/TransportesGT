package org.transportsgt.ticketservice.repositories;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.transportsgt.ticketservice.models.Boleto;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BoletoRepository extends JpaRepository<Boleto, UUID> {

    /**
     * Mapa de asientos: los ocupados ({@code RESERVADO} o {@code CONFIRMADO}) de un viaje.
     */
    @Query("""
            SELECT b.numeroAsiento FROM Boleto b
             WHERE b.idViaje = :idViaje
               AND b.estado IN (org.transportsgt.ticketservice.models.EstadoBoleto.RESERVADO, org.transportsgt.ticketservice.models.EstadoBoleto.CONFIRMADO)
             ORDER BY b.numeroAsiento
            """)
    List<Short> findAsientosOcupados(@Param("idViaje") UUID idViaje);

    /**
     * De los asientos pedidos, los que ya están ocupados (para dar un mensaje claro antes de reservar).
     */
    @Query("""
            SELECT b.numeroAsiento FROM Boleto b
             WHERE b.idViaje = :idViaje AND b.numeroAsiento IN :asientos
               AND b.estado IN (org.transportsgt.ticketservice.models.EstadoBoleto.RESERVADO, org.transportsgt.ticketservice.models.EstadoBoleto.CONFIRMADO)
             ORDER BY b.numeroAsiento
            """)
    List<Short> findOcupadosEntre(@Param("idViaje") UUID idViaje, @Param("asientos") Collection<Short> asientos);

    /**
     * Asientos ocupados de varios viajes a la vez (listado de viajes a la venta).
     */
    @Query("""
            SELECT b.idViaje AS idViaje, COUNT(b) AS ocupados FROM Boleto b
             WHERE b.idViaje IN :idsViaje
               AND b.estado IN (org.transportsgt.ticketservice.models.EstadoBoleto.RESERVADO, org.transportsgt.ticketservice.models.EstadoBoleto.CONFIRMADO)
             GROUP BY b.idViaje
            """)
    List<ConteoAsientos> contarOcupados(@Param("idsViaje") Collection<UUID> idsViaje);

    @Query("""
            SELECT COALESCE(MAX(b.numeroAsiento), 0) FROM Boleto b
             WHERE b.idViaje = :idViaje
               AND b.estado IN (org.transportsgt.ticketservice.models.EstadoBoleto.RESERVADO, org.transportsgt.ticketservice.models.EstadoBoleto.CONFIRMADO)
            """)
    Short findAsientoMaximo(@Param("idViaje") UUID idViaje);

    /**
     * Abordaje: el boleto con su compra, bloqueado para que dos lectores no lo registren a la vez.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Boleto b JOIN FETCH b.compra WHERE b.codigo = :codigo")
    Optional<Boleto> findByCodigoParaAbordar(@Param("codigo") String codigo);

    /**
     * Lista de pasajeros: boletos {@code CONFIRMADO} del viaje con su compra, por número de asiento.
     */
    @Query("""
            SELECT b FROM Boleto b JOIN FETCH b.compra
             WHERE b.idViaje = :idViaje
               AND b.estado = org.transportsgt.ticketservice.models.EstadoBoleto.CONFIRMADO
             ORDER BY b.numeroAsiento
            """)
    List<Boleto> findConfirmadosDelViaje(@Param("idViaje") UUID idViaje);
}
