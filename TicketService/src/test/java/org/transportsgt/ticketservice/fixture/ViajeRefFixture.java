package org.transportsgt.ticketservice.fixture;

import org.transportsgt.ticketservice.models.ViajeRef;
import org.transportsgt.eventos.payload.viajes.EstadoViaje;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public final class ViajeRefFixture {

    /** Hora fija del {@code Clock} de las pruebas unitarias. */
    public static final Instant AHORA = Instant.parse("2026-10-20T16:00:00Z");
    public static final UUID SUCURSAL_XELA = UUID.fromString("10000000-0000-4000-8000-000000000001");
    public static final UUID SUCURSAL_CAPITAL = UUID.fromString("10000000-0000-4000-8000-000000000002");

    private ViajeRefFixture() {
    }

    /** Viaje PROGRAMADO de Xela a la Capital, mañana, con bus de 40 asientos y boleto de Q85.00. */
    public static ViajeRef programado(UUID idViaje) {
        OffsetDateTime salida = OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC).plusDays(1);
        return ViajeRef.builder()
                .id(idViaje)
                .idRuta(UUID.randomUUID())
                .idSucursalOrigen(SUCURSAL_XELA)
                .nombreOrigen("Quetzaltenango")
                .nombreDestino("Ciudad de Guatemala")
                .fechaHoraSalida(salida)
                .fechaHoraLlegadaEstimada(salida.plusHours(4))
                .precioBoleto(new BigDecimal("85.00"))
                .idBus(UUID.randomUUID())
                .placaBus("P-123ABC")
                .capacidadBus((short) 40)
                .estado(EstadoViaje.PROGRAMADO)
                .eliminado(false)
                .version(0L)
                .build();
    }
}
