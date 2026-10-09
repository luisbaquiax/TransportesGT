package org.transportsgt.privaterentalservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;
import org.transportsgt.eventos.payload.Canal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Alquiler privado de un bus. Un alquiler, sea de ida o de ida y vuelta, genera un solo viaje en Viajes.
 */
@Entity
@Table(name = "alquiler")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alquiler {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal", nullable = false, updatable = false, length = 10)
    private Canal canal;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 15)
    private EstadoAlquiler estado = EstadoAlquiler.SOLICITADO;

    /** Null si se solicitó en mostrador sin cuenta. */
    @Column(name = "id_usuario_cliente")
    private UUID idUsuarioCliente;

    @Column(name = "nombre_cliente", nullable = false, length = 150)
    private String nombreCliente;

    /** Normalizado; {@code CF} en mostrador sin NIT. */
    @Column(name = "nit_cliente", nullable = false, length = 20)
    private String nitCliente;

    /** sucursal_ref(id). También es la sucursal del ingreso. */
    @Column(name = "id_sucursal_origen", nullable = false)
    private UUID idSucursalOrigen;

    @Column(name = "origen", nullable = false, length = 150)
    private String origen;

    @Column(name = "destino", nullable = false, length = 150)
    private String destino;

    @Column(name = "fecha_hora_salida", nullable = false)
    private OffsetDateTime fechaHoraSalida;

    /** Obligatoria si es ida y vuelta. */
    @Column(name = "fecha_hora_retorno")
    private OffsetDateTime fechaHoraRetorno;

    /** La fija el administrador al cotizar; en ida y vuelta, el regreso al origen. */
    @Column(name = "fecha_hora_llegada_estimada")
    private OffsetDateTime fechaHoraLlegadaEstimada;

    @Builder.Default
    @Column(name = "ida_y_vuelta", nullable = false)
    private Boolean idaYVuelta = false;

    @Column(name = "numero_pasajeros", nullable = false)
    private Short numeroPasajeros;

    /** La indica el solicitante; el administrador puede corregirla al cotizar. */
    @Column(name = "distancia_estimada_km", nullable = false, precision = 8, scale = 2)
    private BigDecimal distanciaEstimadaKm;

    @Column(name = "precio_estimado", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioEstimado;

    @Column(name = "precio_final", precision = 12, scale = 2)
    private BigDecimal precioFinal;

    /** Movimiento de cartera; null si se pagó en efectivo en mostrador. */
    @Column(name = "id_pago")
    private UUID idPago;

    /** Elegida por el usuario. */
    @Column(name = "fecha_pago")
    private LocalDate fechaPago;

    /** Se llena al consumir ViajeProgramado. */
    @Column(name = "id_viaje")
    private UUID idViaje;

    /** Se llenan al consumir ViajeActualizado. */
    @Column(name = "id_bus")
    private UUID idBus;

    @Column(name = "placa_bus", length = 15)
    private String placaBus;

    @Column(name = "id_chofer")
    private UUID idChofer;

    @Column(name = "nombre_chofer", length = 150)
    private String nombreChofer;

    @Column(name = "motivo_cancelacion", length = 30)
    private String motivoCancelacion;

    @Column(name = "cancelado_en")
    private OffsetDateTime canceladoEn;

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
