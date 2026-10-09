package org.transportsgt.notificationservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bitácora de correos, un registro por destinatario. {@code clave_dedup} evita enviar el mismo correo dos veces.
 * Para ENLACE_CONTRASENA el cuerpo no se guarda (texto fijo).
 */
@Entity
@Table(name = "notificacion")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** COMPRA_BOLETOS, LICENCIA_POR_VENCER, ALQUILER_COTIZADO, ENLACE_CONTRASENA, CONTRASENA_CAMBIADA... */
    @Column(name = "tipo", nullable = false, length = 40)
    private String tipo;

    @Column(name = "correo_destino", nullable = false, length = 150)
    private String correoDestino;

    @Column(name = "id_usuario")
    private UUID idUsuario;

    @Column(name = "asunto", nullable = false, length = 200)
    private String asunto;

    @Column(name = "cuerpo", nullable = false, columnDefinition = "text")
    private String cuerpo;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 10)
    private EstadoNotificacion estado = EstadoNotificacion.PENDIENTE;

    @Builder.Default
    @Column(name = "intentos", nullable = false)
    private Short intentos = 0;

    @Column(name = "ultimo_error", columnDefinition = "text")
    private String ultimoError;

    /** Espera exponencial entre reintentos. */
    @Column(name = "proximo_intento_en")
    private OffsetDateTime proximoIntentoEn;

    /** {@code evt:{idEvento}:{correo}} o {@code lic:{idRecordatorio}:{correo}}. */
    @Column(name = "clave_dedup", nullable = false, unique = true, length = 200)
    private String claveDedup;

    @Column(name = "id_evento_origen")
    private UUID idEventoOrigen;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "enviada_en")
    private OffsetDateTime enviadaEn;
}
