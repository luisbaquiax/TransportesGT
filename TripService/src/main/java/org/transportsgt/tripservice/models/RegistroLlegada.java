package org.transportsgt.tripservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Registro de llegada de un viaje. Inmutable: {@code distancia_recorrida_km} se calcula antes de insertar.
 */
@Entity
@Table(name = "registro_llegada")
@Immutable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistroLlegada {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_viaje", nullable = false, unique = true)
    private Viaje viaje;

    @Column(name = "fecha_hora_llegada_real", nullable = false)
    private OffsetDateTime fechaHoraLlegadaReal;

    @Column(name = "kilometraje_final", nullable = false, precision = 10, scale = 1)
    private BigDecimal kilometrajeFinal;

    @Column(name = "distancia_recorrida_km", nullable = false, precision = 10, scale = 1)
    private BigDecimal distanciaRecorridaKm;

    @Column(name = "gasto_combustible_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal gastoCombustibleTotal;

    /** Instantánea al momento de la llegada. */
    @Column(name = "salario_base_chofer", nullable = false, precision = 10, scale = 2)
    private BigDecimal salarioBaseChofer;

    @Column(name = "registrado_por", nullable = false)
    private UUID registradoPor;

    @CreationTimestamp
    @Column(name = "registrado_en", nullable = false, updatable = false)
    private OffsetDateTime registradoEn;
}
