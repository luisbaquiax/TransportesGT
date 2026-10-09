package org.transportsgt.privaterentalservice.models;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Proyección de las sucursales de Identidad (SucursalCreada/Actualizada). El servicio nunca la modifica por su cuenta.
 */
@Entity
@Table(name = "sucursal_ref")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SucursalRef {

    /** = idSucursal del evento. */
    @Id
    private UUID id;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "activa", nullable = false)
    private Boolean activa;

    /** versionAgregado del último evento aplicado. */
    @Column(name = "version", nullable = false)
    private Long version;
}
