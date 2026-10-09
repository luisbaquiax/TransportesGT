package org.transportsgt.reportingservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.transportsgt.eventos.payload.TipoLinea;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Gasto de combustible, taller (una fila por línea), depreciación o salario del chofer.
 */
@Entity
@Table(name = "rep_gasto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepGasto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_gasto", nullable = false, length = 20)
    private TipoGasto tipoGasto;

    /** idGasto / idLinea / idDepreciacion / idPago; único por tipo. */
    @Column(name = "id_referencia", nullable = false)
    private UUID idReferencia;

    @Column(name = "id_sucursal", nullable = false)
    private UUID idSucursal;

    @Column(name = "id_bus")
    private UUID idBus;

    @Column(name = "id_viaje")
    private UUID idViaje;

    @Column(name = "fecha_gasto", nullable = false)
    private LocalDate fechaGasto;

    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    /** Solo TALLER. */
    @Column(name = "id_categoria")
    private UUID idCategoria;

    @Column(name = "nombre_categoria", length = 80)
    private String nombreCategoria;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_linea", length = 15)
    private TipoLinea tipoLinea;

    @Column(name = "descripcion", length = 250)
    private String descripcion;

    /** Solo DEPRECIACION. */
    @Column(name = "distancia_km", precision = 10, scale = 1)
    private BigDecimal distanciaKm;

    @Column(name = "monto_por_km", precision = 10, scale = 4)
    private BigDecimal montoPorKm;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;
}
