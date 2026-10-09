package org.transportsgt.ticketservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.transportsgt.eventos.payload.EstadoViaje;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Proyección de los viajes REGULARES (ViajeProgramado/Actualizado/Eliminado). Solo se venden los PROGRAMADO
 * con salida futura.
 */
@Entity
@Table(name = "viaje_ref")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ViajeRef {

    /** = idViaje del evento. */
    @Id
    private UUID id;

    @Column(name = "id_ruta", nullable = false)
    private UUID idRuta;

    @Column(name = "id_sucursal_origen", nullable = false)
    private UUID idSucursalOrigen;

    @Column(name = "nombre_origen", nullable = false, length = 150)
    private String nombreOrigen;

    @Column(name = "nombre_destino", nullable = false, length = 150)
    private String nombreDestino;

    @Column(name = "fecha_hora_salida", nullable = false)
    private OffsetDateTime fechaHoraSalida;

    @Column(name = "fecha_hora_llegada_estimada", nullable = false)
    private OffsetDateTime fechaHoraLlegadaEstimada;

    @Column(name = "precio_boleto", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioBoleto;

    @Column(name = "id_bus")
    private UUID idBus;

    @Column(name = "placa_bus", length = 15)
    private String placaBus;

    /** El trigger {@code fn_validar_asiento} valida cada asiento contra este valor. */
    @Column(name = "capacidad_bus")
    private Short capacidadBus;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 25)
    private EstadoViaje estado;

    @Builder.Default
    @Column(name = "eliminado", nullable = false)
    private Boolean eliminado = false;

    /** versionAgregado del último evento aplicado. */
    @Column(name = "version", nullable = false)
    private Long version;
}
