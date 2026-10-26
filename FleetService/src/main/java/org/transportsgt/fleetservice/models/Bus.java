package org.transportsgt.fleetservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;
import org.transportsgt.eventos.payload.EstadoOperativo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bus de una sucursal. No se elimina, solo se desactiva ({@code activo = false}).
 */
@Entity
@Table(name = "bus")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bus {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** sucursal_ref(id). */
    @Column(name = "id_sucursal", nullable = false)
    private UUID idSucursal;

    /** Única sin distinguir mayúsculas ({@code uq_bus_placa}). */
    @Column(name = "placa", nullable = false, length = 15)
    private String placa;

    @Column(name = "marca", nullable = false, length = 50)
    private String marca;

    @Column(name = "modelo", nullable = false, length = 50)
    private String modelo;

    @Column(name = "anio_fabricacion", nullable = false)
    private Short anioFabricacion;

    @Column(name = "capacidad_pasajeros", nullable = false)
    private Short capacidadPasajeros;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_operativo", nullable = false, length = 20)
    private EstadoOperativo estadoOperativo = EstadoOperativo.DISPONIBLE;

    /** Se actualiza al consumir SalidaRegistrada / LlegadaRegistrada; nunca baja. */
    @Builder.Default
    @Column(name = "kilometraje_actual", nullable = false, precision = 10, scale = 1)
    private BigDecimal kilometrajeActual = BigDecimal.ZERO;

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
