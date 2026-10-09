package org.transportsgt.ticketservice.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
     * Historial del usuario autenticado: sus compras en línea y las de mostrador hechas con su NIT
     * (también las anteriores a crear la cuenta). {@code nit} sale de cliente_ref, nunca de la petición.
     */
    @Query("""
            SELECT c FROM Compra c
             WHERE c.idUsuarioComprador = :idUsuario OR (:nit IS NOT NULL AND c.nitComprador = :nit)
             ORDER BY c.creadoEn DESC
            """)
    Page<Compra> findHistorial(@Param("idUsuario") UUID idUsuario, @Param("nit") String nit, Pageable pageable);

    /** ViajeEliminado: compras que hay que anular. */
    @EntityGraph(attributePaths = "boletos")
    List<Compra> findAllByIdViajeAndEstadoIn(UUID idViaje, Collection<EstadoCompra> estados);

    /** Job de recuperación: reservas sin confirmar cuyo plazo venció. */
    List<Compra> findAllByEstadoAndReservaExpiraEnBefore(EstadoCompra estado, OffsetDateTime limite);
}
