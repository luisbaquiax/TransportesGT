package org.transportsgt.ticketservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.transportsgt.eventos.payload.Canal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Compra de uno o varios boletos de un viaje, en línea (pago con cartera) o en mostrador.
 * El {@code id} lo asigna el servicio antes de guardar: es el {@code idCompra} y la {@code Clave-Idempotencia}
 * del pago en Cartera.
 */
@Entity
@Table(name = "compra")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Compra {

    @Id
    private UUID id;

    /** viaje_ref(id). */
    @Column(name = "id_viaje", nullable = false, updatable = false)
    private UUID idViaje;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal", nullable = false, updatable = false, length = 10)
    private Canal canal;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 12)
    private EstadoCompra estado = EstadoCompra.RESERVADA;

    @Column(name = "cantidad_boletos", nullable = false)
    private Short cantidadBoletos;

    @Column(name = "precio_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioUnitario;

    @Column(name = "monto_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoTotal;

    /** Obligatorio en EN_LINEA; null en venta de mostrador sin cuenta. */
    @Column(name = "id_usuario_comprador")
    private UUID idUsuarioComprador;

    /** Normalizado; {@code CF} en mostrador sin NIT. */
    @Builder.Default
    @Column(name = "nit_comprador", nullable = false, length = 20)
    private String nitComprador = "CF";

    @Column(name = "nombre_comprador", nullable = false, length = 150)
    private String nombreComprador;

    /** Movimiento de cartera (solo EN_LINEA). */
    @Column(name = "id_pago")
    private UUID idPago;

    /** Elegida por el usuario; obligatoria al confirmar. */
    @Column(name = "fecha_pago")
    private LocalDate fechaPago;

    @Column(name = "id_sucursal_ingreso", nullable = false)
    private UUID idSucursalIngreso;

    /** Cajero o administrador (MOSTRADOR). */
    @Column(name = "id_usuario_vendedor")
    private UUID idUsuarioVendedor;

    /** Las reservas sin pago vencidas las anula un job. */
    @Column(name = "reserva_expira_en")
    private OffsetDateTime reservaExpiraEn;

    @Column(name = "motivo_anulacion", length = 30)
    private String motivoAnulacion;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "confirmado_en")
    private OffsetDateTime confirmadoEn;

    @Column(name = "anulado_en")
    private OffsetDateTime anuladoEn;

    @Builder.Default
    @OneToMany(mappedBy = "compra", cascade = CascadeType.PERSIST)
    @OrderBy("numeroAsiento")
    private List<Boleto> boletos = new ArrayList<>();
}
