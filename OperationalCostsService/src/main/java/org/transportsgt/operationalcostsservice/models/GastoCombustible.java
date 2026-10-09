package org.transportsgt.operationalcostsservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Gasto de combustible de un viaje, generado al consumir LlegadaRegistrada. Inmutable; {@code UNIQUE(id_viaje)}
 * hace idempotente el consumo.
 */
@Entity
@Table(name = "gasto_combustible")
@Immutable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GastoCombustible {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "id_viaje", nullable = false, unique = true)
    private UUID idViaje;

    /** bus_ref(id). */
    @Column(name = "id_bus", nullable = false)
    private UUID idBus;

    /** sucursal_ref(id): la del bus. */
    @Column(name = "id_sucursal", nullable = false)
    private UUID idSucursal;

    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(name = "fecha_gasto", nullable = false)
    private LocalDate fechaGasto;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;
}
