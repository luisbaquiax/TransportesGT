package org.transportsgt.tripservice.models;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Proyección de los choferes de Flota (ChoferRegistrado/Actualizado).
 */
@Entity
@Table(name = "chofer_ref")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChoferRef {

    /** = idChofer del evento. */
    @Id
    private UUID id;

    /** Usuario con rol CHOFER: solo él registra la salida y la llegada de sus viajes. */
    @Column(name = "id_usuario", unique = true)
    private UUID idUsuario;

    @Column(name = "id_sucursal", nullable = false)
    private UUID idSucursal;

    @Column(name = "nombre_completo", nullable = false, length = 150)
    private String nombreCompleto;

    /** Instantánea que viaja en LlegadaRegistrada. */
    @Column(name = "salario_base_viaje", nullable = false, precision = 10, scale = 2)
    private BigDecimal salarioBaseViaje;

    @Column(name = "fecha_vencimiento_licencia", nullable = false)
    private LocalDate fechaVencimientoLicencia;

    @Column(name = "activo", nullable = false)
    private Boolean activo;

    /** versionAgregado del último evento aplicado. */
    @Column(name = "version", nullable = false)
    private Long version;
}
