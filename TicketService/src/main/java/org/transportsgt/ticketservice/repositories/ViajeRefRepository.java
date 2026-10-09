package org.transportsgt.ticketservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.transportsgt.eventos.payload.EstadoViaje;
import org.transportsgt.ticketservice.models.ViajeRef;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface ViajeRefRepository extends JpaRepository<ViajeRef, UUID> {

    /** Viajes a la venta: programados, no eliminados y con salida futura. */
    List<ViajeRef> findAllByEliminadoFalseAndEstadoAndFechaHoraSalidaAfterOrderByFechaHoraSalida(
            EstadoViaje estado, OffsetDateTime desde);

    List<ViajeRef> findAllByIdRutaAndEliminadoFalseAndEstadoAndFechaHoraSalidaAfterOrderByFechaHoraSalida(
            UUID idRuta, EstadoViaje estado, OffsetDateTime desde);

    /**
     * Aplica ViajeProgramado/Actualizado (solo REGULAR). Ignora el evento si su versión no es mayor que la guardada.
     * No toca {@code eliminado}: un ViajeActualizado atrasado no revive un viaje eliminado.
     *
     * @param estado nombre del enum {@code EstadoViaje}
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO viaje_ref (id, id_ruta, id_sucursal_origen, nombre_origen, nombre_destino, fecha_hora_salida,
                                   fecha_hora_llegada_estimada, precio_boleto, id_bus, placa_bus, capacidad_bus,
                                   estado, version)
            VALUES (:id, :idRuta, :idSucursalOrigen, :nombreOrigen, :nombreDestino, :fechaHoraSalida,
                    :fechaHoraLlegadaEstimada, :precioBoleto, CAST(:idBus AS uuid), CAST(:placaBus AS varchar),
                    CAST(:capacidadBus AS smallint), :estado, :version)
            ON CONFLICT (id) DO UPDATE
               SET id_ruta = EXCLUDED.id_ruta, id_sucursal_origen = EXCLUDED.id_sucursal_origen,
                   nombre_origen = EXCLUDED.nombre_origen, nombre_destino = EXCLUDED.nombre_destino,
                   fecha_hora_salida = EXCLUDED.fecha_hora_salida,
                   fecha_hora_llegada_estimada = EXCLUDED.fecha_hora_llegada_estimada,
                   precio_boleto = EXCLUDED.precio_boleto, id_bus = EXCLUDED.id_bus, placa_bus = EXCLUDED.placa_bus,
                   capacidad_bus = EXCLUDED.capacidad_bus, estado = EXCLUDED.estado, version = EXCLUDED.version
             WHERE viaje_ref.version < EXCLUDED.version
            """)
    int upsert(@Param("id") UUID id, @Param("idRuta") UUID idRuta, @Param("idSucursalOrigen") UUID idSucursalOrigen,
               @Param("nombreOrigen") String nombreOrigen, @Param("nombreDestino") String nombreDestino,
               @Param("fechaHoraSalida") OffsetDateTime fechaHoraSalida,
               @Param("fechaHoraLlegadaEstimada") OffsetDateTime fechaHoraLlegadaEstimada,
               @Param("precioBoleto") BigDecimal precioBoleto, @Param("idBus") UUID idBus,
               @Param("placaBus") String placaBus, @Param("capacidadBus") Short capacidadBus,
               @Param("estado") String estado, @Param("version") long version);

    /** ViajeEliminado. */
    @Modifying
    @Query("UPDATE ViajeRef v SET v.eliminado = true WHERE v.id = :id")
    int marcarEliminado(@Param("id") UUID id);
}
