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
 * Depreciación de un viaje con la tarifa vigente a la fecha de llegada (instantánea en {@code monto_por_km}).
 * Inmutable; {@code UNIQUE(id_viaje)} hace idempotente el consumo.
 */
@Entity
@Table(name = "registro_depreciacion")
@Immutable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistroDepreciacion {

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

    @Column(name = "distancia_km", nullable = false, precision = 10, scale = 1)
    private BigDecimal distanciaKm;

    @Column(name = "monto_por_km", nullable = false, precision = 10, scale = 4)
    private BigDecimal montoPorKm;

    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(name = "fecha_calculo", nullable = false)
    private LocalDate fechaCalculo;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;
}
