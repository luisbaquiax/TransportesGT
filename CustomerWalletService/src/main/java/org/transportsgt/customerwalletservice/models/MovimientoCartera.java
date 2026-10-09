package org.transportsgt.customerwalletservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;
import org.transportsgt.eventos.payload.TipoReferencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Libro de movimientos de la cartera. Inmutable: el trigger {@code trg_movimiento_inmutable} bloquea UPDATE y DELETE.
 * Su {@code id} es el {@code idPago} / {@code idMovimiento} de los eventos y de la API.
 */
@Entity
@Table(name = "movimiento_cartera")
@Immutable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovimientoCartera {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cartera", nullable = false)
    private Cartera cartera;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 10)
    private TipoMovimiento tipo;

    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(name = "saldo_posterior", nullable = false, precision = 12, scale = 2)
    private BigDecimal saldoPosterior;

    /**
     * Obligatoria en PAGO y REEMBOLSO; nula en RECARGA ({@code ck_mov_referencia}).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_referencia", length = 10)
    private TipoReferencia tipoReferencia;

    /**
     * idCompra o idAlquiler.
     */
    @Column(name = "id_referencia")
    private UUID idReferencia;

    /**
     * Encabezado {@code Clave-Idempotencia} (obligatorio en recargas y pagos).
     */
    @Column(name = "clave_idempotencia", unique = true, length = 100)
    private String claveIdempotencia;

    /**
     * Fecha de negocio elegida por el usuario; en reembolsos, la fecha del evento en hora de Guatemala.
     */
    @Column(name = "fecha_movimiento", nullable = false)
    private LocalDate fechaMovimiento;

    @Column(name = "descripcion", length = 250)
    private String descripcion;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;
}
