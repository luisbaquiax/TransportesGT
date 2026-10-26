package org.transportsgt.privaterentalservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Historial de precios de un alquiler: el estimado por el sistema y cada ajuste del administrador.
 */
@Entity
@Table(name = "cotizacion_alquiler")
@Immutable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CotizacionAlquiler {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_alquiler", nullable = false)
    private Alquiler alquiler;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 15)
    private TipoCotizacion tipo;

    @Column(name = "precio", nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;

    /** Null cuando es ESTIMADA por el sistema. */
    @Column(name = "id_usuario")
    private UUID idUsuario;

    /** En ESTIMADA, las tarifas usadas. */
    @Column(name = "comentario", length = 250)
    private String comentario;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;
}
