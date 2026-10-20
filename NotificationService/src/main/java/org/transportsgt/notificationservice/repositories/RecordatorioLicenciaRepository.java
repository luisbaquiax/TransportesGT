package org.transportsgt.notificationservice.repositories;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.transportsgt.notificationservice.models.RecordatorioLicencia;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecordatorioLicenciaRepository extends JpaRepository<RecordatorioLicencia, UUID>,
        JpaSpecificationExecutor<RecordatorioLicencia> {

    /**
     * Job diario: avisos pendientes cuya fecha ya llegó. Cada uno se procesa después en su propia transacción.
     */
    @Query("""
            SELECT r.id FROM RecordatorioLicencia r
             WHERE r.estado = org.transportsgt.notificationservice.models.EstadoRecordatorio.PENDIENTE
               AND r.fechaEnvioProgramada <= :hoy
             ORDER BY r.fechaEnvioProgramada
            """)
    List<UUID> findIdsPendientesHasta(@Param("hoy") LocalDate hoy);

    /**
     * Bloquea el aviso con su chofer: dos instancias del job no lo envían dos veces.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RecordatorioLicencia r JOIN FETCH r.chofer WHERE r.id = :id")
    Optional<RecordatorioLicencia> findByIdParaActualizar(@Param("id") UUID id);

    /**
     * Bitácora de avisos: el chofer viene en la misma consulta (nombre y sucursal).
     */
    @Override
    @EntityGraph(attributePaths = "chofer")
    Page<RecordatorioLicencia> findAll(Specification<RecordatorioLicencia> spec, Pageable pageable);

    /**
     * Programa un aviso; si ya existe (mismo chofer, vencimiento y anticipación) no hace nada.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO recordatorio_licencia (id, id_chofer, fecha_vencimiento, dias_anticipacion, fecha_envio_programada)
            VALUES (gen_random_uuid(), :idChofer, :fechaVencimiento, :diasAnticipacion, :fechaEnvioProgramada)
            ON CONFLICT (id_chofer, fecha_vencimiento, dias_anticipacion) DO NOTHING
            """)
    int programar(@Param("idChofer") UUID idChofer, @Param("fechaVencimiento") LocalDate fechaVencimiento,
                  @Param("diasAnticipacion") short diasAnticipacion,
                  @Param("fechaEnvioProgramada") LocalDate fechaEnvioProgramada);

    /**
     * Cambió la fecha de vencimiento: cancela los avisos pendientes de la fecha anterior.
     */
    @Modifying
    @Query("""
            UPDATE RecordatorioLicencia r SET r.estado = org.transportsgt.notificationservice.models.EstadoRecordatorio.CANCELADO
             WHERE r.chofer.idChofer = :idChofer AND r.estado = org.transportsgt.notificationservice.models.EstadoRecordatorio.PENDIENTE
               AND r.fechaVencimiento <> :fechaVigente
            """)
    int cancelarPendientesDeOtraFecha(@Param("idChofer") UUID idChofer, @Param("fechaVigente") LocalDate fechaVigente);

    /**
     * Chofer desactivado: cancela todos sus avisos pendientes.
     */
    @Modifying
    @Query("""
            UPDATE RecordatorioLicencia r SET r.estado = org.transportsgt.notificationservice.models.EstadoRecordatorio.CANCELADO
             WHERE r.chofer.idChofer = :idChofer AND r.estado = org.transportsgt.notificationservice.models.EstadoRecordatorio.PENDIENTE
            """)
    int cancelarPendientes(@Param("idChofer") UUID idChofer);

    /**
     * Chofer reactivado, o que vuelve a una fecha de vencimiento anterior: {@code uq_recordatorio} impide volver a
     * insertar esos avisos, así que los {@code CANCELADO} de la fecha vigente que aún no pasaron vuelven a
     * {@code PENDIENTE}.
     */
    @Modifying
    @Query("""
            UPDATE RecordatorioLicencia r SET r.estado = org.transportsgt.notificationservice.models.EstadoRecordatorio.PENDIENTE
             WHERE r.chofer.idChofer = :idChofer AND r.estado = org.transportsgt.notificationservice.models.EstadoRecordatorio.CANCELADO
               AND r.fechaVencimiento = :fechaVigente AND r.fechaEnvioProgramada >= :hoy
            """)
    int reactivarCancelados(@Param("idChofer") UUID idChofer, @Param("fechaVigente") LocalDate fechaVigente,
                            @Param("hoy") LocalDate hoy);
}
