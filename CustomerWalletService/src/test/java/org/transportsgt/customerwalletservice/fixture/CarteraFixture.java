package org.transportsgt.customerwalletservice.fixture;

import org.transportsgt.customerwalletservice.models.Cartera;
import org.transportsgt.customerwalletservice.models.Cliente;
import org.transportsgt.customerwalletservice.models.MovimientoCartera;
import org.transportsgt.customerwalletservice.models.TipoMovimiento;
import org.transportsgt.eventos.payload.costos.TipoReferencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class CarteraFixture {

    public static final LocalDate FECHA = LocalDate.parse("2026-10-20");

    private CarteraFixture() {
    }

    public static Cartera cartera(Cliente cliente, String saldo) {
        return Cartera.builder()
                .id(UUID.randomUUID())
                .cliente(cliente)
                .saldo(new BigDecimal(saldo))
                .version(0L)
                .actualizadoEn(OffsetDateTime.parse("2026-10-20T16:00:00Z"))
                .build();
    }

    public static MovimientoCartera recarga(Cartera cartera, String monto, String clave) {
        return MovimientoCartera.builder()
                .id(UUID.randomUUID())
                .cartera(cartera)
                .tipo(TipoMovimiento.RECARGA)
                .monto(new BigDecimal(monto))
                .saldoPosterior(cartera.getSaldo())
                .claveIdempotencia(clave)
                .fechaMovimiento(FECHA)
                .descripcion("Recarga de saldo")
                .build();
    }

    public static MovimientoCartera pago(Cartera cartera, TipoReferencia tipoReferencia, UUID idReferencia,
                                         String monto) {
        return MovimientoCartera.builder()
                .id(UUID.randomUUID())
                .cartera(cartera)
                .tipo(TipoMovimiento.PAGO)
                .monto(new BigDecimal(monto))
                .saldoPosterior(cartera.getSaldo())
                .tipoReferencia(tipoReferencia)
                .idReferencia(idReferencia)
                .claveIdempotencia(idReferencia.toString())
                .fechaMovimiento(FECHA)
                .build();
    }

    public static MovimientoCartera reembolso(Cartera cartera, TipoReferencia tipoReferencia, UUID idReferencia,
                                              String monto) {
        return MovimientoCartera.builder()
                .id(UUID.randomUUID())
                .cartera(cartera)
                .tipo(TipoMovimiento.REEMBOLSO)
                .monto(new BigDecimal(monto))
                .saldoPosterior(cartera.getSaldo())
                .tipoReferencia(tipoReferencia)
                .idReferencia(idReferencia)
                .fechaMovimiento(FECHA)
                .build();
    }
}
