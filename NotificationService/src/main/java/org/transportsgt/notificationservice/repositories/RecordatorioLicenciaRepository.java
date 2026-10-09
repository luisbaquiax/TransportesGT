package org.transportsgt.notificationservice.repositories;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.transportsgt.notificationservice.models.EstadoRecordatorio;
import org.transportsgt.notificationservice.models.RecordatorioLicencia;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RecordatorioLicenciaRepository extends JpaRepository<RecordatorioLicencia, UUID> {

    /** Scheduler diario: avisos pendientes cuya fecha ya llegó, con su chofer. */
    @EntityGraph(attributePaths = "chofer")
    List<RecordatorioLicencia> findAllByEstadoAndFechaEnvioProgramadaLessThanEqualOrderByFechaEnvioProgramada(
            EstadoRecordatorio estado, LocalDate hoy);

    List<RecordatorioLicencia> findAllByChoferIdChoferOrderByFechaEnvioProgramada(UUID idChofer);

    /** Programa un aviso; si ya existe (mismo chofer, vencimiento y anticipación) no hace nada. */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO recordatorio_licencia (id, id_chofer, fecha_vencimiento, dias_anticipacion, fecha_envio_programada)
            VALUES (gen_random_uuid(), :idChofer, :fechaVencimiento, :diasAnticipacion, :fechaEnvioProgramada)
            ON CONFLICT (id_chofer, fecha_vencimiento, dias_anticipacion) DO NOTHING
            """)
    int programar(@Param("idChofer") UUID idChofer, @Param("fechaVencimiento") LocalDate fechaVencimiento,
                  @Param("diasAnticipacion") short diasAnticipacion,
                  @Param("fechaEnvioProgramada") LocalDate fechaEnvioProgramada);

    /** Cambió la fecha de vencimiento: cancela los avisos pendientes de la fecha anterior. */
    @Modifying
    @Query("""
            UPDATE RecordatorioLicencia r SET r.estado = org.transportsgt.notificationservice.models.EstadoRecordatorio.CANCELADO
             WHERE r.chofer.idChofer = :idChofer AND r.estado = org.transportsgt.notificationservice.models.EstadoRecordatorio.PENDIENTE
               AND r.fechaVencimiento <> :fechaVigente
            """)
    int cancelarPendientesDeOtraFecha(@Param("idChofer") UUID idChofer, @Param("fechaVigente") LocalDate fechaVigente);

    /** Chofer desactivado: cancela todos sus avisos pendientes. */
    @Modifying
    @Query("""
            UPDATE RecordatorioLicencia r SET r.estado = org.transportsgt.notificationservice.models.EstadoRecordatorio.CANCELADO
             WHERE r.chofer.idChofer = :idChofer AND r.estado = org.transportsgt.notificationservice.models.EstadoRecordatorio.PENDIENTE
            """)
    int cancelarPendientes(@Param("idChofer") UUID idChofer);
}
