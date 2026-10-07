package org.transportsgt.identityservice.repositories;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.transportsgt.eventos.payload.PropositoContrasena;
import org.transportsgt.identityservice.models.TokenContrasena;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface TokenContrasenaRepository extends JpaRepository<TokenContrasena, UUID> {

    @EntityGraph(attributePaths = "usuario")
    Optional<TokenContrasena> findByHashToken(String hashToken);

    /** Límite de solicitudes de "olvidé mi contraseña" (3 por hora). */
    long countByUsuarioIdAndPropositoAndCreadoEnAfter(UUID idUsuario, PropositoContrasena proposito,
                                                      OffsetDateTime desde);

    /**
     * Invalida los tokens activos del usuario para un propósito, antes de emitir uno nuevo.
     * Incluye los ya vencidos: si no, ocuparían {@code uq_token_activo} e impedirían crear el siguiente.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE TokenContrasena t SET t.invalidadoEn = :ahora
             WHERE t.usuario.id = :idUsuario AND t.proposito = :proposito
               AND t.usadoEn IS NULL AND t.invalidadoEn IS NULL
            """)
    int invalidarActivos(@Param("idUsuario") UUID idUsuario,
                         @Param("proposito") PropositoContrasena proposito,
                         @Param("ahora") OffsetDateTime ahora);

    /** Invalida todos los tokens activos del usuario, de cualquier propósito (tras restablecer la contraseña). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE TokenContrasena t SET t.invalidadoEn = :ahora
             WHERE t.usuario.id = :idUsuario AND t.usadoEn IS NULL AND t.invalidadoEn IS NULL
            """)
    int invalidarTodosActivos(@Param("idUsuario") UUID idUsuario, @Param("ahora") OffsetDateTime ahora);

    /** Job diario: borra los tokens vencidos antes de {@code limite} (hace más de 7 días). */
    @Modifying
    @Query("DELETE FROM TokenContrasena t WHERE t.expiraEn < :limite")
    int eliminarVencidosAntesDe(@Param("limite") OffsetDateTime limite);
}
