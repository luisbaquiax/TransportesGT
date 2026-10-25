package org.transportsgt.reportingservice.fixture;

import org.transportsgt.reportingservice.repositories.reportsadmin.IngresoConsulta;
import org.transportsgt.reportingservice.repositories.reportssucursal.IngresoAlquilerConsulta;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class RepIngresoFixture {

    private RepIngresoFixture() {
    }

    public record Ingreso(UUID getIdSucursal, String getSucursal, LocalDate getFecha, String getTipo,
                          UUID getReferencia, String getRutaOrigen, String getRutaDestino, String getOrigen,
                          String getDestino, String getCliente, String getNit, Integer getCantidadBoletos,
                          String getCanal, BigDecimal getMonto) implements IngresoConsulta {
    }

    public static Ingreso venta(UUID idSucursal, String sucursal, String monto, Integer boletos) {
        return new Ingreso(idSucursal, sucursal, EventoFixture.FECHA_PAGO, "VENTA_BOLETOS", UUID.randomUUID(),
                "Quetzaltenango", "Ciudad de Guatemala", null, null, "Ana López", "1234567K", boletos, "EN_LINEA",
                new BigDecimal(monto));
    }

    public static Ingreso alquiler(UUID idSucursal, String sucursal, String monto) {
        return new Ingreso(idSucursal, sucursal, EventoFixture.FECHA_PAGO, "ALQUILER", UUID.randomUUID(), null, null,
                "Huehuetenango", "Sololá", "Empresa Ñandú", "CF", null, "MOSTRADOR", new BigDecimal(monto));
    }

    public record IngresoAlquiler(UUID getIdAlquiler, String getCliente, String getNit, String getOrigen,
                                  String getDestino, Instant getFechaSalida, Instant getFechaRetorno,
                                  String getPlaca, LocalDate getFechaPago, BigDecimal getPrecioTotal)
            implements IngresoAlquilerConsulta {
    }

    public static IngresoAlquiler ingresoAlquiler(String precio, Instant retorno) {
        return new IngresoAlquiler(UUID.randomUUID(), "Empresa Ñandú", "CF", "Quetzaltenango", "San Marcos",
                RepViajeFixture.SALIDA, retorno, "P900ABC", EventoFixture.FECHA_PAGO, new BigDecimal(precio));
    }
}
