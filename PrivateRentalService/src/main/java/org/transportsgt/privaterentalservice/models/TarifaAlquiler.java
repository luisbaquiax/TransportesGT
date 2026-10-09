package org.transportsgt.privaterentalservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Tarifas del precio estimado ({@code distancia × precio_por_km × (2 si ida y vuelta) + días × precio_por_dia}).
 * Las cambia solo el administrador de sistema; se conserva el historial y un cambio el mismo día reemplaza al anterior.
 */
@Entity
@Table(name = "tarifa_alquiler")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TarifaAlquiler {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "precio_por_km", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioPorKm;

    @Column(name = "precio_por_dia", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioPorDia;

    @Column(name = "vigente_desde", nullable = false, unique = true)
    private LocalDate vigenteDesde;

    @Column(name = "creado_por", nullable = false)
    private UUID creadoPor;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;
}
