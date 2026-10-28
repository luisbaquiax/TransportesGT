package org.transportsgt.reportingservice.fixture;

import org.transportsgt.eventos.payload.flota.EstadoOperativo;
import org.transportsgt.reportingservice.models.RepBus;
import org.transportsgt.reportingservice.repositories.reportssucursal.BusConsulta;
import org.transportsgt.reportingservice.repositories.reportssucursal.DepreciacionConsulta;

import java.math.BigDecimal;
import java.util.UUID;

public final class RepBusFixture {

    private RepBusFixture() {
    }

    public static RepBus bus(UUID id, UUID idSucursal, String placa) {
        return RepBus.builder().id(id).idSucursal(idSucursal).placa(placa).marca("Toyota").modelo("Hiace")
                .capacidadPasajeros((short) 45).estadoOperativo(EstadoOperativo.DISPONIBLE).activo(true)
                .kilometrajeActual(new BigDecimal("1000.0")).version(1L).build();
    }

    public record Bus(UUID getIdBus, String getPlaca, String getMarca, String getModelo, Integer getCapacidad,
                      String getEstadoOperativo, String getChofer, BigDecimal getKilometrajeActual,
                      Long getViajesRealizados, Boolean getActivo) implements BusConsulta {
    }

    public static Bus consulta(String placa, String chofer, Long viajes, Boolean activo) {
        return new Bus(UUID.randomUUID(), placa, "Volvo", "B12R", 50, "DISPONIBLE", chofer,
                new BigDecimal("52100.7"), viajes, activo);
    }

    public record Depreciacion(UUID getIdBus, String getPlaca, BigDecimal getKmRecorridos,
                               BigDecimal getDepreciacionAcumulada) implements DepreciacionConsulta {
    }

    public static Depreciacion depreciacion(String placa, String km, String acumulada) {
        return new Depreciacion(UUID.randomUUID(), placa, new BigDecimal(km), new BigDecimal(acumulada));
    }
}
