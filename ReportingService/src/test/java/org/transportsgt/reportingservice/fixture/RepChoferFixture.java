package org.transportsgt.reportingservice.fixture;

import org.transportsgt.reportingservice.repositories.reportssucursal.ChoferConsulta;

import java.time.LocalDate;
import java.util.UUID;

public final class RepChoferFixture {

    private RepChoferFixture() {
    }

    public record Chofer(UUID getIdChofer, String getNumeroLicencia, String getNombre, String getTipoLicencia,
                         LocalDate getFechaVencimiento, Boolean getActivo, Long getViajesRealizados)
            implements ChoferConsulta {
    }

    public static Chofer consulta(String nombre, LocalDate vencimiento, Boolean activo, Long viajes) {
        return new Chofer(UUID.randomUUID(), "LIC-" + nombre.length(), nombre, "A", vencimiento, activo, viajes);
    }
}
