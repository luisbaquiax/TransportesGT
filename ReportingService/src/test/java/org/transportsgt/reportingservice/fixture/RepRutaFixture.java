package org.transportsgt.reportingservice.fixture;

import org.transportsgt.reportingservice.models.RepRuta;
import org.transportsgt.reportingservice.repositories.reportsadmin.MapaRutaConsulta;
import org.transportsgt.reportingservice.repositories.reportsadmin.RutaDemandadaConsulta;

import java.math.BigDecimal;
import java.util.UUID;

public final class RepRutaFixture {

    private RepRutaFixture() {
    }

    public static RepRuta ruta(UUID id, UUID origen, UUID destino) {
        return RepRuta.builder().id(id).idSucursalOrigen(origen).idSucursalDestino(destino)
                .distanciaKm(new BigDecimal("205.50")).precioBoleto(new BigDecimal("85.00")).eliminada(false)
                .version(1L).build();
    }

    public record RutaDemandada(UUID getIdRuta, String getOrigen, String getDestino, BigDecimal getDistanciaKm,
                                BigDecimal getPrecioBoleto, Boolean getEliminada, Long getBoletosVendidos,
                                Long getViajesConVentas, BigDecimal getIngreso) implements RutaDemandadaConsulta {
    }

    public static RutaDemandada demandada(String origen, String destino, Long boletos, String ingreso,
                                          Boolean eliminada) {
        return new RutaDemandada(UUID.randomUUID(), origen, destino, new BigDecimal("120.00"), new BigDecimal("55"),
                eliminada, boletos, boletos == null ? null : 1L, new BigDecimal(ingreso));
    }

    public record MapaRuta(UUID getIdRuta, String getOrigen, BigDecimal getLatitudOrigen,
                           BigDecimal getLongitudOrigen, String getDestino, BigDecimal getLatitudDestino,
                           BigDecimal getLongitudDestino, BigDecimal getDistanciaKm, BigDecimal getPrecioBoleto)
            implements MapaRutaConsulta {
    }

    public static MapaRuta mapa(String origen, String destino) {
        return new MapaRuta(UUID.randomUUID(), origen, new BigDecimal("14.834700"), new BigDecimal("-91.518100"),
                destino, new BigDecimal("14.642700"), new BigDecimal("-90.513300"), new BigDecimal("205.50"),
                new BigDecimal("85.00"));
    }
}
