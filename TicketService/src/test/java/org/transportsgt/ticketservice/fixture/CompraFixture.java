package org.transportsgt.ticketservice.fixture;

import org.transportsgt.ticketservice.models.Boleto;
import org.transportsgt.ticketservice.models.Compra;
import org.transportsgt.ticketservice.models.EstadoBoleto;
import org.transportsgt.ticketservice.models.EstadoCompra;
import org.transportsgt.ticketservice.models.ViajeRef;
import org.transportsgt.eventos.payload.boletos.Canal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public final class CompraFixture {

    public static final LocalDate FECHA_PAGO = LocalDate.parse("2026-10-20");

    private CompraFixture() {
    }

    /** Compra en línea RESERVADA de los asientos dados, sin persistir. */
    public static Compra reservada(UUID idCompra, ViajeRef viaje, UUID idComprador, int... asientos) {
        Compra compra = Compra.builder()
                .id(idCompra)
                .idViaje(viaje.getId())
                .canal(Canal.EN_LINEA)
                .estado(EstadoCompra.RESERVADA)
                .cantidadBoletos((short) asientos.length)
                .precioUnitario(viaje.getPrecioBoleto())
                .montoTotal(viaje.getPrecioBoleto().multiply(BigDecimal.valueOf(asientos.length)))
                .idUsuarioComprador(idComprador)
                .nitComprador(ClienteRefFixture.NIT)
                .nombreComprador(ClienteRefFixture.NOMBRE)
                .fechaPago(FECHA_PAGO)
                .idSucursalIngreso(viaje.getIdSucursalOrigen())
                .reservaExpiraEn(OffsetDateTime.ofInstant(ViajeRefFixture.AHORA, ZoneOffset.UTC).plusMinutes(10))
                .version(0L)
                .build();
        for (int asiento : asientos) {
            compra.getBoletos().add(Boleto.builder()
                    .id(UUID.randomUUID())
                    .compra(compra)
                    .idViaje(viaje.getId())
                    .numeroAsiento((short) asiento)
                    .estado(EstadoBoleto.RESERVADO)
                    .codigo("BOL-" + asiento)
                    .build());
        }
        return compra;
    }

    public static Compra confirmada(UUID idCompra, ViajeRef viaje, UUID idComprador, int... asientos) {
        Compra compra = reservada(idCompra, viaje, idComprador, asientos);
        compra.setEstado(EstadoCompra.CONFIRMADA);
        compra.setIdPago(UUID.randomUUID());
        compra.getBoletos().forEach(b -> b.setEstado(EstadoBoleto.CONFIRMADO));
        return compra;
    }

    public static Compra anulada(UUID idCompra, ViajeRef viaje, UUID idComprador, int... asientos) {
        Compra compra = reservada(idCompra, viaje, idComprador, asientos);
        compra.setEstado(EstadoCompra.ANULADA);
        compra.setMotivoAnulacion("VIAJE_ELIMINADO");
        compra.getBoletos().forEach(b -> b.setEstado(EstadoBoleto.ANULADO));
        return compra;
    }

    public static Compra deMostrador(UUID idCompra, ViajeRef viaje, UUID idVendedor, int... asientos) {
        Compra compra = confirmada(idCompra, viaje, null, asientos);
        compra.setCanal(Canal.MOSTRADOR);
        compra.setIdPago(null);
        compra.setIdUsuarioVendedor(idVendedor);
        return compra;
    }
}
