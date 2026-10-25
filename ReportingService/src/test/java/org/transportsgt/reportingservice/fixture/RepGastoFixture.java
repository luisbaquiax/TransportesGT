package org.transportsgt.reportingservice.fixture;

import org.transportsgt.reportingservice.repositories.reportsadmin.GastoConsulta;
import org.transportsgt.reportingservice.repositories.reportsadmin.GastoTallerConsulta;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class RepGastoFixture {

    private RepGastoFixture() {
    }

    public record Gasto(UUID getIdSucursal, String getSucursal, LocalDate getFecha, String getTipoGasto,
                        String getTipoLinea, UUID getIdReferencia, String getPlaca, String getCategoria,
                        String getDescripcion, String getChofer, BigDecimal getDistanciaKm,
                        BigDecimal getMontoPorKm, BigDecimal getMonto) implements GastoConsulta {
    }

    public static Gasto gasto(UUID idSucursal, String sucursal, String tipoGasto, String tipoLinea, String monto) {
        return new Gasto(idSucursal, sucursal, EventoFixture.FECHA_PAGO, tipoGasto, tipoLinea, UUID.randomUUID(),
                "P900ABC", "Frenos", "Pastillas", "José Méndez", new BigDecimal("205.5"), new BigDecimal("2.0000"),
                new BigDecimal(monto));
    }

    public record Taller(String getCategoria, UUID getIdBus, String getPlaca, String getSucursal, LocalDate getFecha,
                         String getDescripcion, String getTipoLinea, BigDecimal getMonto)
            implements GastoTallerConsulta {
    }

    public static Taller taller(String categoria, UUID idBus, String placa, String monto) {
        return new Taller(categoria, idBus, placa, "Quetzaltenango", EventoFixture.FECHA_PAGO, "Detalle", "REPUESTO",
                new BigDecimal(monto));
    }
}
