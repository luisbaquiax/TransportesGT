package org.transportsgt.operationalcostsservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Historial de la tarifa de depreciación por kilómetro. Cambiarla no altera los registros ya calculados.
 */
@Entity
@Table(name = "configuracion_depreciacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfiguracionDepreciacion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "monto_por_km", nullable = false, precision = 10, scale = 4)
    private BigDecimal montoPorKm;

    /** Un segundo cambio con la misma fecha reemplaza al anterior. */
    @Column(name = "vigente_desde", nullable = false, unique = true)
    private LocalDate vigenteDesde;

    /** Administrador de sistema. */
    @Column(name = "creado_por", nullable = false)
    private UUID creadoPor;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;
}
