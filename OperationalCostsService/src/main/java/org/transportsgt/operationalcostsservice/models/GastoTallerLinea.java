package org.transportsgt.operationalcostsservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;
import org.transportsgt.eventos.payload.TipoLinea;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Línea de un gasto de taller: categoría obligatoria, tipo (mano de obra o repuesto) y monto. Inmutable.
 */
@Entity
@Table(name = "gasto_taller_linea")
@Immutable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GastoTallerLinea {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_gasto_taller", nullable = false)
    private GastoTaller gastoTaller;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_categoria", nullable = false)
    private CategoriaGasto categoria;

    @Column(name = "descripcion", nullable = false, length = 250)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 15)
    private TipoLinea tipo;

    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;
}
