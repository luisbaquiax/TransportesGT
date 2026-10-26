package org.transportsgt.tripservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Registro de salida de un viaje. Inmutable: no se modifica ni se elimina bajo ninguna circunstancia.
 */
@Entity
@Table(name = "registro_salida")
@Immutable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistroSalida {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_viaje", nullable = false, unique = true)
    private Viaje viaje;

    @Column(name = "id_bus", nullable = false)
    private UUID idBus;

    @Column(name = "id_chofer", nullable = false)
    private UUID idChofer;

    @Column(name = "fecha_hora_salida_real", nullable = false)
    private OffsetDateTime fechaHoraSalidaReal;

    @Column(name = "kilometraje_inicial", nullable = false, precision = 10, scale = 1)
    private BigDecimal kilometrajeInicial;

    /** Administrador de sucursal o el chofer asignado. */
    @Column(name = "registrado_por", nullable = false)
    private UUID registradoPor;

    @CreationTimestamp
    @Column(name = "registrado_en", nullable = false, updatable = false)
    private OffsetDateTime registradoEn;
}
