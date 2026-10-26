package org.transportsgt.reportingservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.transportsgt.eventos.payload.Canal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Ingreso por venta de boletos (VentaBoletosRegistrada) o por alquiler (AlquilerConfirmado). La anulación
 * (BoletosAnulados, AlquilerCancelado) lo marca {@code anulado} sobre su fecha de pago original.
 */
@Entity
@Table(name = "rep_ingreso")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepIngreso {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_ingreso", nullable = false, length = 15)
    private TipoIngreso tipoIngreso;

    /** idCompra o idAlquiler; único por tipo. */
    @Column(name = "id_referencia", nullable = false)
    private UUID idReferencia;

    @Column(name = "id_sucursal", nullable = false)
    private UUID idSucursal;

    /** Fecha de pago elegida por el usuario. */
    @Column(name = "fecha_ingreso", nullable = false)
    private LocalDate fechaIngreso;

    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(name = "id_viaje")
    private UUID idViaje;

    @Column(name = "id_ruta")
    private UUID idRuta;

    @Column(name = "cantidad_boletos")
    private Short cantidadBoletos;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal", length = 10)
    private Canal canal;

    @Column(name = "nit_cliente", length = 20)
    private String nitCliente;

    @Column(name = "nombre_cliente", length = 150)
    private String nombreCliente;

    @Column(name = "origen", length = 150)
    private String origen;

    @Column(name = "destino", length = 150)
    private String destino;

    @Column(name = "fecha_salida")
    private OffsetDateTime fechaSalida;

    @Column(name = "fecha_retorno")
    private OffsetDateTime fechaRetorno;

    @Builder.Default
    @Column(name = "anulado", nullable = false)
    private Boolean anulado = false;

    @Column(name = "anulado_en")
    private OffsetDateTime anuladoEn;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;
}
