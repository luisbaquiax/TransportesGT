package org.transportsgt.operationalcostsservice.models;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Proyección de los buses de Flota (BusRegistrado/Actualizado): taller, combustible y depreciación son por bus.
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

    /** sucursal_ref(id). */
    @Column(name = "id_sucursal", nullable = false)
    private UUID idSucursal;

    @Column(name = "placa", nullable = false, length = 15)
    private String placa;

    @Column(name = "activo", nullable = false)
    private Boolean activo;

    /** versionAgregado del último evento aplicado. */
    @Column(name = "version", nullable = false)
    private Long version;
}
