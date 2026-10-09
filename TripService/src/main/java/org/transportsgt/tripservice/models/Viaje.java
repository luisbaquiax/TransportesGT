package org.transportsgt.tripservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;
import org.transportsgt.eventos.payload.EstadoViaje;
import org.transportsgt.eventos.payload.TipoViaje;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Viaje regular (con ruta y precio) o de alquiler privado (con alquiler y número de pasajeros).
 * El tipo es inmutable; bus y chofer solo pueden faltar en PENDIENTE_ASIGNACION. {@code version} es también el
 * {@code versionAgregado} de ViajeProgramado/Actualizado.
 */
@Entity
@Table(name = "viaje")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Viaje {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_viaje", nullable = false, updatable = false, length = 20)
    private TipoViaje tipoViaje;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 25)
    private EstadoViaje estado;

    /** Solo REGULAR. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_ruta")
    private Ruta ruta;

    /** Solo ALQUILER_PRIVADO (idempotencia al consumir AlquilerConfirmado). */
    @Column(name = "id_alquiler", unique = true, updatable = false)
    private UUID idAlquiler;

    /** sucursal_ref(id). */
    @Column(name = "id_sucursal_origen", nullable = false)
    private UUID idSucursalOrigen;

    @Column(name = "nombre_origen", nullable = false, length = 150)
    private String nombreOrigen;

    @Column(name = "nombre_destino", nullable = false, length = 150)
    private String nombreDestino;

    /** bus_ref(id). */
    @Column(name = "id_bus")
    private UUID idBus;

    /** chofer_ref(id). */
    @Column(name = "id_chofer")
    private UUID idChofer;

    @Column(name = "fecha_hora_salida", nullable = false)
    private OffsetDateTime fechaHoraSalida;

    @Column(name = "fecha_hora_llegada_estimada", nullable = false)
    private OffsetDateTime fechaHoraLlegadaEstimada;

    /** Instantánea del precio de la ruta (solo REGULAR). */
    @Column(name = "precio_boleto", precision = 10, scale = 2)
    private BigDecimal precioBoleto;

    /** Solo ALQUILER_PRIVADO: el bus asignado debe tener al menos esta capacidad. */
    @Column(name = "numero_pasajeros")
    private Short numeroPasajeros;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "creado_por", nullable = false, updatable = false)
    private UUID creadoPor;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private OffsetDateTime actualizadoEn;
}
