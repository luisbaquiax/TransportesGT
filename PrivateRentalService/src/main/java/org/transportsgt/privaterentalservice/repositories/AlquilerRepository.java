package org.transportsgt.privaterentalservice.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.transportsgt.privaterentalservice.models.Alquiler;
import org.transportsgt.privaterentalservice.models.EstadoAlquiler;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlquilerRepository extends JpaRepository<Alquiler, UUID>, JpaSpecificationExecutor<Alquiler> {

    /** ViajeActualizado, ViajeEliminado y LlegadaRegistrada llegan con el idViaje. */
    Optional<Alquiler> findByIdViaje(UUID idViaje);

    /** El administrador de sucursal solo gestiona los alquileres de su sucursal. */
    Optional<Alquiler> findByIdAndIdSucursalOrigen(UUID id, UUID idSucursalOrigen);

    /**
     * Historial del usuario autenticado: sus alquileres en línea y los de mostrador hechos con su NIT
     * (también los anteriores a crear la cuenta). {@code nit} sale de cliente_ref, nunca de la petición.
     */
    @Query("""
            SELECT a FROM Alquiler a
             WHERE a.idUsuarioCliente = :idUsuario OR (:nit IS NOT NULL AND a.nitCliente = :nit)
             ORDER BY a.creadoEn DESC
            """)
    Page<Alquiler> findHistorial(@Param("idUsuario") UUID idUsuario, @Param("nit") String nit, Pageable pageable);

    /** Bandeja del administrador: solicitudes por cotizar, cotizados sin pagar, etc. */
    List<Alquiler> findAllByIdSucursalOrigenAndEstadoInOrderByFechaHoraSalida(
            UUID idSucursalOrigen, Collection<EstadoAlquiler> estados);
}
