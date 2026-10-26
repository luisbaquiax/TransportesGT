package org.transportsgt.tripservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Ruta de sucursal a sucursal. La gestiona solo el administrador de la sucursal de origen y se elimina solo si no
 * tiene viajes ({@code ON DELETE RESTRICT}).
 */
@Entity
@Table(name = "ruta")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ruta {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** sucursal_ref(id). */
    @Column(name = "id_sucursal_origen", nullable = false, updatable = false)
    private UUID idSucursalOrigen;

    /** sucursal_ref(id). Distinta del origen; el par origen-destino es único. */
    @Column(name = "id_sucursal_destino", nullable = false)
    private UUID idSucursalDestino;

    @Column(name = "distancia_km", nullable = false, precision = 8, scale = 2)
    private BigDecimal distanciaKm;

    @Column(name = "precio_boleto", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioBoleto;

    @Column(name = "creado_por", nullable = false, updatable = false)
    private UUID creadoPor;

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
