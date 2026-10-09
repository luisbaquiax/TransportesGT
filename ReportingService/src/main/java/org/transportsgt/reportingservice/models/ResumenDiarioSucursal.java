package org.transportsgt.reportingservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Vista {@code v_resumen_diario_sucursal}: ingresos, gastos y ganancia por sucursal y día (base del reporte de
 * ganancias). Se mapea con {@code @Subselect} para que la validación del esquema no la trate como tabla.
 */
@Entity
@Immutable
@Subselect("SELECT id_sucursal, fecha, ingresos, gastos, ganancia FROM v_resumen_diario_sucursal")
@Synchronize({"rep_ingreso", "rep_gasto"})
@IdClass(ResumenDiarioSucursal.Clave.class)
@Getter
@NoArgsConstructor
public class ResumenDiarioSucursal {

    @Id
    @Column(name = "id_sucursal")
    private UUID idSucursal;

    @Id
    @Column(name = "fecha")
    private LocalDate fecha;

    @Column(name = "ingresos")
    private BigDecimal ingresos;

    @Column(name = "gastos")
    private BigDecimal gastos;

    @Column(name = "ganancia")
    private BigDecimal ganancia;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Clave implements Serializable {
        private UUID idSucursal;
        private LocalDate fecha;
    }
}
