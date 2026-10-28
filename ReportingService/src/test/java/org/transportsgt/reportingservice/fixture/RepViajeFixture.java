package org.transportsgt.reportingservice.fixture;

import org.transportsgt.eventos.payload.viajes.EstadoViaje;
import org.transportsgt.eventos.payload.viajes.TipoViaje;
import org.transportsgt.reportingservice.models.RepViaje;
import org.transportsgt.reportingservice.repositories.reportssucursal.IngresoBoletoConsulta;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class RepViajeFixture {

    /** 12:00 UTC = 06:00 en Guatemala. */
    public static final Instant SALIDA = Instant.parse("2026-10-25T12:00:00Z");

    private RepViajeFixture() {
    }

    public static RepViaje viaje(UUID id, UUID idBus) {
        return RepViaje.builder().id(id).tipoViaje(TipoViaje.REGULAR).estado(EstadoViaje.PROGRAMADO)
                .idSucursalOrigen(RepSucursalFixture.QUETZALTENANGO).idBus(idBus).eliminado(false).version(1L).build();
    }

    public record IngresoBoleto(UUID getIdViaje, String getOrigen, String getDestino, Instant getFechaHoraSalida,
                                String getPlaca, Long getBoletosVendidos, BigDecimal getIngreso)
            implements IngresoBoletoConsulta {
    }

    public static IngresoBoleto ingresoBoleto(Long boletos, String ingreso) {
        return new IngresoBoleto(UUID.randomUUID(), "Quetzaltenango", "Ciudad de Guatemala", SALIDA, "P900ABC",
                boletos, new BigDecimal(ingreso));
    }
}
