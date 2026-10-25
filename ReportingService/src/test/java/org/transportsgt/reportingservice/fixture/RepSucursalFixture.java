package org.transportsgt.reportingservice.fixture;

import org.transportsgt.reportingservice.models.RepSucursal;
import org.transportsgt.reportingservice.repositories.catalogos.OpcionConsulta;
import org.transportsgt.reportingservice.repositories.reportsadmin.GananciaConsulta;

import java.math.BigDecimal;
import java.util.UUID;

public final class RepSucursalFixture {

    public static final UUID QUETZALTENANGO = UUID.fromString("10000000-0000-4000-8000-000000000001");
    public static final UUID GUATEMALA = UUID.fromString("10000000-0000-4000-8000-000000000002");
    public static final UUID HUEHUETENANGO = UUID.fromString("10000000-0000-4000-8000-000000000003");

    private RepSucursalFixture() {
    }

    public static RepSucursal sucursal(UUID id, String nombre) {
        return RepSucursal.builder().id(id).nombre(nombre).departamento(nombre).latitud(new BigDecimal("14.834700"))
                .longitud(new BigDecimal("-91.518100")).activa(true).version(1L).build();
    }

    public static RepSucursal quetzaltenango() {
        return sucursal(QUETZALTENANGO, "Quetzaltenango");
    }

    public record Ganancia(UUID getIdSucursal, String getSucursal, BigDecimal getIngresos, BigDecimal getGastos,
                           BigDecimal getGanancia) implements GananciaConsulta {
    }

    public static Ganancia ganancia(UUID id, String nombre, String ingresos, String gastos) {
        BigDecimal i = new BigDecimal(ingresos);
        BigDecimal g = new BigDecimal(gastos);
        return new Ganancia(id, nombre, i, g, i.subtract(g));
    }

    public record Opcion(UUID getId, String getNombre, Boolean getActivo) implements OpcionConsulta {
    }

    public static Opcion opcion(String nombre, Boolean activo) {
        return new Opcion(UUID.randomUUID(), nombre, activo);
    }
}
