package org.transportsgt.reportingservice.models;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Proyección de las rutas (RutaCreada/Actualizada/Eliminada). Una ruta eliminada se conserva para los
 * reportes históricos.
 */
@Entity
@Table(name = "rep_ruta")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepRuta {

    @Id
    private UUID id;

    @Column(name = "id_sucursal_origen", nullable = false)
    private UUID idSucursalOrigen;

    @Column(name = "id_sucursal_destino", nullable = false)
    private UUID idSucursalDestino;

    @Column(name = "distancia_km", precision = 8, scale = 2)
    private BigDecimal distanciaKm;

    @Column(name = "precio_boleto", precision = 10, scale = 2)
    private BigDecimal precioBoleto;

    @Builder.Default
    @Column(name = "eliminada", nullable = false)
    private Boolean eliminada = false;

    /** versionAgregado del último evento aplicado. */
    @Column(name = "version", nullable = false)
    private Long version;
}
