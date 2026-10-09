package org.transportsgt.fleetservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;
import org.transportsgt.eventos.payload.TipoLicencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Chofer de una sucursal. No se elimina, solo se desactiva; solo si no tiene viajes activos.
 */
@Entity
@Table(name = "chofer")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Chofer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Usuario de Identidad (rol CHOFER), si tiene acceso al sistema. No se valida contra Identidad. */
    @Column(name = "id_usuario", unique = true)
    private UUID idUsuario;

    /** sucursal_ref(id). */
    @Column(name = "id_sucursal", nullable = false)
    private UUID idSucursal;

    @Column(name = "nombre_completo", nullable = false, length = 150)
    private String nombreCompleto;

    /** Para los avisos de vencimiento de licencia. */
    @Column(name = "correo", nullable = false, length = 150)
    private String correo;

    @Column(name = "telefono", nullable = false, length = 20)
    private String telefono;

    @Column(name = "numero_licencia", nullable = false, unique = true, length = 30)
    private String numeroLicencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_licencia", nullable = false, length = 2)
    private TipoLicencia tipoLicencia;

    @Column(name = "fecha_vencimiento_licencia", nullable = false)
    private LocalDate fechaVencimientoLicencia;

    @Column(name = "salario_base_viaje", nullable = false, precision = 10, scale = 2)
    private BigDecimal salarioBaseViaje;

    @Column(name = "foto_url", length = 500)
    private String fotoUrl;

    @Builder.Default
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

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
