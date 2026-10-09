package org.transportsgt.reportingservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.transportsgt.eventos.payload.TipoLicencia;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Proyección de los choferes (ChoferRegistrado/Actualizado) para el listado general de choferes.
 */
@Entity
@Table(name = "rep_chofer")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepChofer {

    @Id
    private UUID id;

    @Column(name = "id_sucursal", nullable = false)
    private UUID idSucursal;

    @Column(name = "numero_licencia", length = 30)
    private String numeroLicencia;

    @Column(name = "nombre_completo", nullable = false, length = 150)
    private String nombreCompleto;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_licencia", length = 2)
    private TipoLicencia tipoLicencia;

    @Column(name = "fecha_vencimiento_licencia")
    private LocalDate fechaVencimientoLicencia;

    @Column(name = "activo", nullable = false)
    private Boolean activo;

    /** versionAgregado del último evento aplicado. */
    @Column(name = "version", nullable = false)
    private Long version;
}
