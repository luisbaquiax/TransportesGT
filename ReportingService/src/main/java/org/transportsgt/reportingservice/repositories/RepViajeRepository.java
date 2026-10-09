package org.transportsgt.reportingservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.transportsgt.eventos.payload.EstadoViaje;
import org.transportsgt.reportingservice.models.RepViaje;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RepViajeRepository extends JpaRepository<RepViaje, UUID>, JpaSpecificationExecutor<RepViaje> {

    Optional<RepViaje> findByIdAlquiler(UUID idAlquiler);

    /** Total de viajes realizados (listados de buses y choferes). */
    long countByIdBusAndEstadoAndEliminadoFalse(UUID idBus, EstadoViaje estado);

    long countByIdChoferAndEstadoAndEliminadoFalse(UUID idChofer, EstadoViaje estado);

    /**
     * Aplica ViajeProgramado/Actualizado. Ignora el evento si su versión no es mayor que la guardada. No toca los
     * datos reales de salida y llegada ni {@code eliminado}.
     *
     * @param tipoViaje nombre del enum {@code TipoViaje}
     * @param estado    nombre del enum {@code EstadoViaje}
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO rep_viaje (id, tipo_viaje, estado, id_ruta, id_alquiler, id_sucursal_origen, nombre_origen,
                                   nombre_destino, id_bus, id_chofer, fecha_hora_salida, fecha_hora_llegada_estimada,
                                   version)
            VALUES (:id, :tipoViaje, :estado, CAST(:idRuta AS uuid), CAST(:idAlquiler AS uuid), :idSucursalOrigen,
                    :nombreOrigen, :nombreDestino, CAST(:idBus AS uuid), CAST(:idChofer AS uuid), :fechaHoraSalida,
                    :fechaHoraLlegadaEstimada, :version)
            ON CONFLICT (id) DO UPDATE
               SET tipo_viaje = EXCLUDED.tipo_viaje, estado = EXCLUDED.estado, id_ruta = EXCLUDED.id_ruta,
                   id_alquiler = EXCLUDED.id_alquiler, id_sucursal_origen = EXCLUDED.id_sucursal_origen,
                   nombre_origen = EXCLUDED.nombre_origen, nombre_destino = EXCLUDED.nombre_destino,
                   id_bus = EXCLUDED.id_bus, id_chofer = EXCLUDED.id_chofer,
                   fecha_hora_salida = EXCLUDED.fecha_hora_salida,
                   fecha_hora_llegada_estimada = EXCLUDED.fecha_hora_llegada_estimada, version = EXCLUDED.version
             WHERE rep_viaje.version < EXCLUDED.version
            """)
    int upsert(@Param("id") UUID id, @Param("tipoViaje") String tipoViaje, @Param("estado") String estado,
               @Param("idRuta") UUID idRuta, @Param("idAlquiler") UUID idAlquiler,
               @Param("idSucursalOrigen") UUID idSucursalOrigen, @Param("nombreOrigen") String nombreOrigen,
               @Param("nombreDestino") String nombreDestino, @Param("idBus") UUID idBus,
               @Param("idChofer") UUID idChofer, @Param("fechaHoraSalida") OffsetDateTime fechaHoraSalida,
               @Param("fechaHoraLlegadaEstimada") OffsetDateTime fechaHoraLlegadaEstimada,
               @Param("version") long version);

    /** SalidaRegistrada. */
    @Modifying
    @Query("""
            UPDATE RepViaje v SET v.fechaHoraSalidaReal = :fechaHoraSalidaReal, v.kilometrajeInicial = :kilometrajeInicial
             WHERE v.id = :id
            """)
    int registrarSalida(@Param("id") UUID id, @Param("fechaHoraSalidaReal") OffsetDateTime fechaHoraSalidaReal,
                        @Param("kilometrajeInicial") BigDecimal kilometrajeInicial);

    /** LlegadaRegistrada: trae también los datos de salida, por si SalidaRegistrada llegó después. */
    @Modifying
    @Query("""
            UPDATE RepViaje v
               SET v.fechaHoraSalidaReal = :fechaHoraSalidaReal, v.fechaHoraLlegadaReal = :fechaHoraLlegadaReal,
                   v.kilometrajeInicial = :kilometrajeInicial, v.kilometrajeFinal = :kilometrajeFinal,
                   v.distanciaRecorridaKm = :distanciaRecorridaKm
             WHERE v.id = :id
            """)
    int registrarLlegada(@Param("id") UUID id, @Param("fechaHoraSalidaReal") OffsetDateTime fechaHoraSalidaReal,
                         @Param("fechaHoraLlegadaReal") OffsetDateTime fechaHoraLlegadaReal,
                         @Param("kilometrajeInicial") BigDecimal kilometrajeInicial,
                         @Param("kilometrajeFinal") BigDecimal kilometrajeFinal,
                         @Param("distanciaRecorridaKm") BigDecimal distanciaRecorridaKm);

    /** ViajeEliminado. */
    @Modifying
    @Query("UPDATE RepViaje v SET v.eliminado = true WHERE v.id = :id")
    int marcarEliminado(@Param("id") UUID id);
}
