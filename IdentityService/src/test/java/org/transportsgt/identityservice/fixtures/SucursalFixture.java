package org.transportsgt.identityservice.fixtures;

import org.transportsgt.identityservice.models.Sucursal;

import java.math.BigDecimal;
import java.util.UUID;

public final class SucursalFixture {

    public static final String NOMBRE = "Quetzaltenango";

    private SucursalFixture() {
    }

    public static Sucursal activa(UUID id) {
        return Sucursal.builder()
                .id(id)
                .nombre(NOMBRE)
                .departamento("Quetzaltenango")
                .direccion("4a. Calle 12-35, Zona 1")
                .telefono("77651234")
                .latitud(new BigDecimal("14.834700"))
                .longitud(new BigDecimal("-91.518100"))
                .activa(true)
                .version(0L)
                .build();
    }

    public static Sucursal inactiva(UUID id) {
        Sucursal sucursal = activa(id);
        sucursal.setActiva(false);
        return sucursal;
    }
}
