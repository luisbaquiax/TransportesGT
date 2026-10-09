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
 * Salario del chofer por viaje (cuenta como gasto): {@code salario_base × 1.00} en viaje regular o {@code × 1.15}
 * en alquiler privado. Inmutable; {@code UNIQUE(id_viaje)} hace idempotente el consumo.
 */
@Entity
@Table(name = "pago_chofer")
@Immutable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagoChofer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "id_viaje", nullable = false, unique = true)
    private UUID idViaje;

    @Column(name = "id_chofer", nullable = false)
    private UUID idChofer;

    /** sucursal_ref(id). */
    @Column(name = "id_sucursal", nullable = false)
    private UUID idSucursal;

    @Column(name = "salario_base", nullable = false, precision = 10, scale = 2)
    private BigDecimal salarioBase;

    @Column(name = "multiplicador", nullable = false, precision = 4, scale = 2)
    private BigDecimal multiplicador;

    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(name = "fecha_pago", nullable = false)
    private LocalDate fechaPago;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;
}
