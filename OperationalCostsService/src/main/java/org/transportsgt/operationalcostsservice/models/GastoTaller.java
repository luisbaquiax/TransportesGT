package org.transportsgt.operationalcostsservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Encabezado de un gasto de taller. Inmutable; {@code total} debe ser igual a la suma de sus líneas
 * (lo verifica el trigger diferido {@code trg_taller_total}) y debe tener al menos una línea.
 */
@Entity
@Table(name = "gasto_taller")
@Immutable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GastoTaller {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** bus_ref(id). */
    @Column(name = "id_bus", nullable = false)
    private UUID idBus;

    /** sucursal_ref(id): la del bus. */
    @Column(name = "id_sucursal", nullable = false)
    private UUID idSucursal;

    /** Elegida por el usuario. */
    @Column(name = "fecha_servicio", nullable = false)
    private LocalDate fechaServicio;

    @Column(name = "total", nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "observaciones", length = 500)
    private String observaciones;

    @Column(name = "registrado_por", nullable = false)
    private UUID registradoPor;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @Builder.Default
    @OneToMany(mappedBy = "gastoTaller", cascade = CascadeType.PERSIST)
    private List<GastoTallerLinea> lineas = new ArrayList<>();
}
