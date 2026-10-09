package org.transportsgt.tripservice.repositories;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.transportsgt.eventos.payload.EstadoViaje;
import org.transportsgt.tripservice.models.Viaje;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ViajeRepository extends JpaRepository<Viaje, UUID>, JpaSpecificationExecutor<Viaje> {

    /** Para armar ViajeProgramado/Actualizado, que lleva datos de la ruta. */
    @EntityGraph(attributePaths = "ruta")
    Optional<Viaje> findConRutaById(UUID id);

    /** Idempotencia al consumir AlquilerConfirmado. */
    Optional<Viaje> findByIdAlquiler(UUID idAlquiler);

    boolean existsByIdAlquiler(UUID idAlquiler);

    /** Una ruta con viajes no se elimina. */
    boolean existsByRutaId(UUID idRuta);

    /** GET /viajes/activos?idBus= (Flota, antes de desactivar un bus). */
    List<Viaje> findAllByIdBusAndEstadoInOrderByFechaHoraSalida(UUID idBus, Collection<EstadoViaje> estados);

    /** GET /viajes/activos?idChofer= (Flota, antes de desactivar un chofer). */
    List<Viaje> findAllByIdChoferAndEstadoInOrderByFechaHoraSalida(UUID idChofer, Collection<EstadoViaje> estados);

    /** Viajes futuros de un bus o chofer que se desactivó: se muestran como pendientes al administrador. */
    List<Viaje> findAllByIdBusAndEstadoAndFechaHoraSalidaAfter(UUID idBus, EstadoViaje estado, OffsetDateTime desde);

    List<Viaje> findAllByIdChoferAndEstadoAndFechaHoraSalidaAfter(UUID idChofer, EstadoViaje estado, OffsetDateTime desde);

    /** Listado de alquileres pendientes de asignación de la sucursal. */
    List<Viaje> findAllByIdSucursalOrigenAndEstadoOrderByFechaHoraSalida(UUID idSucursalOrigen, EstadoViaje estado);

    /** Viajes asignados a un chofer (vista del usuario CHOFER). */
    List<Viaje> findAllByIdChoferAndEstadoInOrderByFechaHoraSalidaDesc(UUID idChofer, Collection<EstadoViaje> estados);
}
