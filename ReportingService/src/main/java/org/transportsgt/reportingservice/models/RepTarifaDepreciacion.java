package org.transportsgt.reportingservice.models;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Historial de la tarifa de depreciación (TarifaDepreciacionCambiada), para mostrar la tarifa configurada.
 */
@Entity
@Table(name = "rep_tarifa_depreciacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepTarifaDepreciacion {

    @Id
    @Column(name = "vigente_desde")
    private LocalDate vigenteDesde;

    @Column(name = "monto_por_km", nullable = false, precision = 10, scale = 4)
    private BigDecimal montoPorKm;
}
