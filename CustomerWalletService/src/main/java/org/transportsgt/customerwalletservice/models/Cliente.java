package org.transportsgt.customerwalletservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Perfil comercial de un usuario de Identidad (cualquier rol). Se crea al consumir {@code UsuarioCreado};
 * nombre, correo y {@code activo} los mantiene {@code UsuarioActualizado}, el resto lo edita el propio usuario.
 */
@Entity
@Table(name = "cliente")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "id_usuario", nullable = false, unique = true, updatable = false)
    private UUID idUsuario;

    @Column(name = "nombre_completo", nullable = false, length = 150)
    private String nombreCompleto;

    @Column(name = "correo", nullable = false, length = 150)
    private String correo;

    /**
     * Normalizado con {@code Nit.normalizar()}; único y distinto de {@code CF}. Null mientras el perfil no lo tenga.
     */
    @Column(name = "nit", length = 20)
    private String nit;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "dpi", length = 13)
    private String dpi;

    @Column(name = "telefono", length = 20)
    private String telefono;

    @Column(name = "direccion", length = 250)
    private String direccion;

    @Builder.Default
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    /**
     * {@code versionAgregado} del último UsuarioCreado/Actualizado aplicado: descarta eventos viejos o desordenados.
     */
    @Builder.Default
    @Column(name = "version_usuario", nullable = false)
    private Long versionUsuario = 0L;

    /**
     * También es el {@code versionAgregado} de {@code PerfilClienteActualizado}.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private OffsetDateTime actualizadoEn;
}
