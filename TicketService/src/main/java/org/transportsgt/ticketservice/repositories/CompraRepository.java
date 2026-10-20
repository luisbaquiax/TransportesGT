package org.transportsgt.ticketservice.repositories;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.transportsgt.ticketservice.models.Compra;
import org.transportsgt.ticketservice.models.EstadoCompra;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompraRepository extends JpaRepository<Compra, UUID>, JpaSpecificationExecutor<Compra> {

    @EntityGraph(attributePaths = "boletos")
    Optional<Compra> findConBoletosById(UUID id);

    /**
     * {@code SELECT ... FOR UPDATE}: confirmar y anular la misma compra (saga, job y ViajeEliminado) se serializan.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Compra c WHERE c.id = :id")
    Optional<Compra> findByIdParaActualizar(@Param("id") UUID id);

    /**
     * Historial del usuario autenticado: sus compras en línea y las de mostrador hechas con su NIT (también las
     * anteriores a crear la cuenta). {@code nit} sale de cliente_ref, nunca de la petición.
     */
    @Query("""
            SELECT c FROM Compra c
             WHERE c.idUsuarioComprador = :idUsuario OR c.nitComprador = :nit
             ORDER BY c.creadoEn DESC
            """)
    Page<Compra> findHistorial(@Param("idUsuario") UUID idUsuario, @Param("nit") String nit, Pageable pageable);

    /**
     * Historial cuando el perfil aún no tiene NIT: solo las compras del usuario.
     */
    Page<Compra> findAllByIdUsuarioCompradorOrderByCreadoEnDesc(UUID idUsuario, Pageable pageable);

    /**
     * ViajeEliminado: compras que hay que anular (cada una se anula con bloqueo).
     */
    @Query("SELECT c.id FROM Compra c WHERE c.idViaje = :idViaje AND c.estado IN :estados")
    List<UUID> findIdsPorViajeYEstado(@Param("idViaje") UUID idViaje, @Param("estados") Collection<EstadoCompra> estados);

    /**
     * Job de recuperación: compras {@code RESERVADA} cuya reserva vence antes del límite. Se usa
     * {@code reserva_expira_en} (calculada con el {@code Clock}) y no {@code creado_en} (reloj del sistema).
     */
    @Query("""
            SELECT c.id FROM Compra c
             WHERE c.estado = org.transportsgt.ticketservice.models.EstadoCompra.RESERVADA
               AND c.reservaExpiraEn <= :limite
             ORDER BY c.reservaExpiraEn
            """)
    List<UUID> findIdsReservadasQueVencenAntesDe(@Param("limite") OffsetDateTime limite);
}
