package org.transportsgt.identityservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;
import org.transportsgt.eventos.payload.Rol;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Usuario del sistema. No se elimina, solo se desactiva ({@code activo = false}).
 *
 */
@Entity
@Table(name = "usuario")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Único sin distinguir mayúsculas ({@code uq_usuario_correo}). */
    @Column(name = "correo", nullable = false, length = 150)
    private String correo;

    @Column(name = "hash_contrasena", nullable = false, length = 100)
    private String hashContrasena;

    @Column(name = "nombre_completo", nullable = false, length = 150)
    private String nombreCompleto;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false, length = 20)
    private Rol rol;

    /** Obligatoria para ADMIN_SUCURSAL, CAJERO y CHOFER; nula para ADMIN_SISTEMA y CLIENTE. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_sucursal")
    private Sucursal sucursal;

    @Builder.Default
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    /** Usuario que lo creó (auditoría). */
    @Column(name = "creado_por")
    private UUID creadoPor;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "contrasena_cambiada_en")
    private OffsetDateTime contrasenaCambiadaEn;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private OffsetDateTime actualizadoEn;
}
