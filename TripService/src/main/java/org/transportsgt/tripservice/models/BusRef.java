package org.transportsgt.tripservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.transportsgt.eventos.payload.EstadoOperativo;

import java.util.UUID;

/**
 * Proyección de los buses de Flota (BusRegistrado/Actualizado). {@code estado_operativo} solo cambia con BusActualizado:
 * el paso automático EN_TRANSITO ↔ DISPONIBLE no se publica.
 */
@Entity
@Table(name = "bus_ref")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusRef {

    /** = idBus del evento. */
    @Id
    private UUID id;

    @Column(name = "id_sucursal", nullable = false)
    private UUID idSucursal;

    @Column(name = "placa", nullable = false, length = 15)
    private String placa;

    @Column(name = "capacidad_pasajeros", nullable = false)
    private Short capacidadPasajeros;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_operativo", nullable = false, length = 20)
    private EstadoOperativo estadoOperativo;

    @Column(name = "activo", nullable = false)
    private Boolean activo;

    /** versionAgregado del último evento aplicado. */
    @Column(name = "version", nullable = false)
    private Long version;
}
