package org.transportsgt.notificationservice.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.transportsgt.notificationservice.models.Notificacion;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface NotificacionRepository extends JpaRepository<Notificacion, UUID>, JpaSpecificationExecutor<Notificacion> {

    /** Evita generar el mismo correo dos veces si el evento se reprocesa. */
    boolean existsByClaveDedup(String claveDedup);

    /** Envío y reintentos: pendientes sin espera o cuya espera ya venció, las más antiguas primero. */
    @Query("""
            SELECT n FROM Notificacion n
             WHERE n.estado = org.transportsgt.notificationservice.models.EstadoNotificacion.PENDIENTE
               AND (n.proximoIntentoEn IS NULL OR n.proximoIntentoEn <= :ahora)
             ORDER BY n.creadoEn
            """)
    List<Notificacion> findPendientesDeEnvio(@Param("ahora") OffsetDateTime ahora, Pageable pageable);

    Page<Notificacion> findAllByIdUsuarioOrderByCreadoEnDesc(UUID idUsuario, Pageable pageable);
}
