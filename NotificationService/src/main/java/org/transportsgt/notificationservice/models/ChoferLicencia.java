package org.transportsgt.notificationservice.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Copia local de los choferes (ChoferRegistrado/Actualizado) para programar los avisos de vencimiento de licencia.
 */
@Entity
@Table(name = "chofer_licencia")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChoferLicencia {

    @Id
    @Column(name = "id_chofer")
    private UUID idChofer;

    @Column(name = "id_sucursal", nullable = false)
    private UUID idSucursal;

    @Column(name = "nombre_completo", nullable = false, length = 150)
    private String nombreCompleto;

    @Column(name = "correo", nullable = false, length = 150)
    private String correo;

    @Column(name = "fecha_vencimiento_licencia", nullable = false)
    private LocalDate fechaVencimientoLicencia;

    @Column(name = "activo", nullable = false)
    private Boolean activo;

    /** versionAgregado del último evento aplicado. */
    @Column(name = "version", nullable = false)
    private Long version;
}
