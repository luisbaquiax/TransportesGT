package org.transportsgt.reportingservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.transportsgt.eventos.payload.EstadoViaje;
import org.transportsgt.eventos.payload.TipoViaje;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Proyección de los viajes (ViajeProgramado/Actualizado/Eliminado), completada con SalidaRegistrada y
 * LlegadaRegistrada (fechas reales y kilometraje).
 */
@Entity
@Table(name = "rep_viaje")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepViaje {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_viaje", nullable = false, length = 20)
    private TipoViaje tipoViaje;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 25)
    private EstadoViaje estado;

    @Column(name = "id_ruta")
    private UUID idRuta;

    @Column(name = "id_alquiler")
    private UUID idAlquiler;

    @Column(name = "id_sucursal_origen", nullable = false)
    private UUID idSucursalOrigen;

    @Column(name = "nombre_origen", length = 150)
    private String nombreOrigen;

    @Column(name = "nombre_destino", length = 150)
    private String nombreDestino;

    @Column(name = "id_bus")
    private UUID idBus;

    @Column(name = "id_chofer")
    private UUID idChofer;

    @Column(name = "fecha_hora_salida")
    private OffsetDateTime fechaHoraSalida;

    @Column(name = "fecha_hora_llegada_estimada")
    private OffsetDateTime fechaHoraLlegadaEstimada;

    @Column(name = "fecha_hora_salida_real")
    private OffsetDateTime fechaHoraSalidaReal;

    @Column(name = "fecha_hora_llegada_real")
    private OffsetDateTime fechaHoraLlegadaReal;

    @Column(name = "kilometraje_inicial", precision = 10, scale = 1)
    private BigDecimal kilometrajeInicial;

    @Column(name = "kilometraje_final", precision = 10, scale = 1)
    private BigDecimal kilometrajeFinal;

    @Column(name = "distancia_recorrida_km", precision = 10, scale = 1)
    private BigDecimal distanciaRecorridaKm;

    @Builder.Default
    @Column(name = "eliminado", nullable = false)
    private Boolean eliminado = false;

    /** versionAgregado del último evento aplicado. */
    @Column(name = "version", nullable = false)
    private Long version;
}
