package org.transportsgt.reportingservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.transportsgt.eventos.payload.EstadoOperativo;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Proyección de los buses (BusRegistrado/Actualizado). Estado operativo y kilometraje se derivan además de
 * SalidaRegistrada y LlegadaRegistrada.
 */
@Entity
@Table(name = "rep_bus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepBus {

    @Id
    private UUID id;

    @Column(name = "id_sucursal", nullable = false)
    private UUID idSucursal;

    @Column(name = "placa", nullable = false, length = 15)
    private String placa;

    @Column(name = "marca", length = 50)
    private String marca;

    @Column(name = "modelo", length = 50)
    private String modelo;

    @Column(name = "capacidad_pasajeros")
    private Short capacidadPasajeros;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_operativo", length = 20)
    private EstadoOperativo estadoOperativo;

    @Column(name = "activo", nullable = false)
    private Boolean activo;

    @Builder.Default
    @Column(name = "kilometraje_actual", nullable = false, precision = 10, scale = 1)
    private BigDecimal kilometrajeActual = BigDecimal.ZERO;

    /** versionAgregado del último evento aplicado. */
    @Column(name = "version", nullable = false)
    private Long version;
}
