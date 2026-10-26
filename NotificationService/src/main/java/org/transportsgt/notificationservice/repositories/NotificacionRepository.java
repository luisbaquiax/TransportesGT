package org.transportsgt.notificationservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.transportsgt.notificationservice.models.Notificacion;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface NotificacionRepository extends JpaRepository<Notificacion, UUID>, JpaSpecificationExecutor<Notificacion> {

    Optional<Notificacion> findByClaveDedup(String claveDedup);

    /**
     * Encola un correo
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO notificacion (id, tipo, correo_destino, id_usuario, asunto, cuerpo, estado, intentos,
                                      proximo_intento_en, clave_dedup, id_evento_origen, creado_en)
            VALUES (gen_random_uuid(), :tipo, :correoDestino, CAST(:idUsuario AS uuid), :asunto, :cuerpo, 'PENDIENTE', 0,
                    :ahora, :claveDedup, CAST(:idEventoOrigen AS uuid), :ahora)
            ON CONFLICT (clave_dedup) DO NOTHING
            """)
    int insertarPendiente(@Param("tipo") String tipo, @Param("correoDestino") String correoDestino,
                          @Param("idUsuario") UUID idUsuario, @Param("asunto") String asunto,
                          @Param("cuerpo") String cuerpo, @Param("claveDedup") String claveDedup,
                          @Param("idEventoOrigen") UUID idEventoOrigen, @Param("ahora") OffsetDateTime ahora);

    /**
     * Toma el siguiente correo listo para enviar y lo bloquea hasta el fin de la transacción.
     */
    @Query(nativeQuery = true, value = """
            SELECT * FROM notificacion
             WHERE estado = 'PENDIENTE' AND proximo_intento_en <= :ahora
             ORDER BY proximo_intento_en, creado_en
             LIMIT 1
               FOR UPDATE SKIP LOCKED
            """)
    Optional<Notificacion> bloquearSiguientePendiente(@Param("ahora") OffsetDateTime ahora);
}
