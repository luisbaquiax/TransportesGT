package org.transportsgt.notificationservice.fixture;

import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.boletos.Canal;
import org.transportsgt.eventos.payload.alquileres.DatosAlquilerCancelado;
import org.transportsgt.eventos.payload.alquileres.DatosAlquilerConfirmado;
import org.transportsgt.eventos.payload.alquileres.DatosAlquilerCotizado;
import org.transportsgt.eventos.payload.alquileres.DatosAlquilerSolicitado;
import org.transportsgt.eventos.payload.boletos.DatosBoletosAnulados;
import org.transportsgt.eventos.payload.cartera.DatosCarteraRecargada;
import org.transportsgt.eventos.payload.cartera.DatosCarteraReembolsada;
import org.transportsgt.eventos.payload.flota.DatosChofer;
import org.transportsgt.eventos.payload.identidad.DatosContrasenaCambiada;
import org.transportsgt.eventos.payload.identidad.DatosEnlaceContrasena;
import org.transportsgt.eventos.payload.identidad.DatosUsuario;
import org.transportsgt.eventos.payload.boletos.DatosVentaBoletos;
import org.transportsgt.eventos.payload.identidad.MotivoContrasena;
import org.transportsgt.eventos.payload.identidad.PropositoContrasena;
import org.transportsgt.eventos.payload.identidad.Rol;
import org.transportsgt.eventos.payload.flota.TipoLicencia;
import org.transportsgt.eventos.payload.costos.TipoReferencia;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class EventoFixture {

    /** Hora fija del {@code Clock} de las pruebas unitarias: 10:00 del 20/10/2026 en Guatemala. */
    public static final Instant AHORA = Instant.parse("2026-10-20T16:00:00Z");
    public static final String TOKEN = "tok-secreto-123";
    private static final OffsetDateTime SALIDA = OffsetDateTime.parse("2026-11-10T07:00:00-06:00");

    private EventoFixture() {
    }

    public static <T> SobreEvento<T> sobre(TipoEvento tipo, T datos, Long versionAgregado) {
        return new SobreEvento<>(UUID.randomUUID(), tipo.nombre(), 1, AHORA, tipo.productor().nombreServicio(),
                UUID.randomUUID(), versionAgregado, UUID.randomUUID(), datos);
    }

    public static DatosUsuario usuario(UUID idUsuario, Rol rol, UUID idSucursal) {
        return new DatosUsuario(idUsuario, "u." + idUsuario.toString().substring(0, 8) + "@transportes.gt",
                ContactoFixture.NOMBRE, rol, idSucursal, true);
    }

    public static DatosChofer chofer(UUID idChofer, LocalDate vencimiento, boolean activo) {
        return new DatosChofer(idChofer, null, ContactoFixture.SUCURSAL_XELA, "Juan Pérez", "juan.perez@transportes.gt",
                "55551234", "LIC-123", TipoLicencia.A, vencimiento, new BigDecimal("150.00"), activo);
    }

    public static DatosVentaBoletos venta(UUID idComprador) {
        return new DatosVentaBoletos(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                idComprador == null ? Canal.MOSTRADOR : Canal.EN_LINEA, List.of(12, 13), new BigDecimal("85.00"),
                new BigDecimal("170.00"), idComprador, "1234567K", ContactoFixture.NOMBRE,
                idComprador == null ? null : UUID.randomUUID(), LocalDate.parse("2026-10-20"),
                ContactoFixture.SUCURSAL_XELA, idComprador == null ? UUID.randomUUID() : null);
    }

    public static DatosBoletosAnulados anulacion(UUID idComprador, Canal canal, UUID idPago) {
        return new DatosBoletosAnulados(UUID.randomUUID(), UUID.randomUUID(), "VIAJE_ELIMINADO", canal,
                new BigDecimal("170.00"), idComprador, idPago, ContactoFixture.SUCURSAL_XELA);
    }

    public static DatosCarteraRecargada recarga(UUID idUsuario) {
        return new DatosCarteraRecargada(UUID.randomUUID(), UUID.randomUUID(), idUsuario, new BigDecimal("200.00"),
                new BigDecimal("350.00"), LocalDate.parse("2026-10-20"));
    }

    public static DatosCarteraReembolsada reembolso(UUID idUsuario, TipoReferencia tipo) {
        return new DatosCarteraReembolsada(UUID.randomUUID(), UUID.randomUUID(), idUsuario, new BigDecimal("170.00"),
                new BigDecimal("520.00"), tipo, UUID.randomUUID(), "VIAJE_ELIMINADO");
    }

    public static DatosAlquilerSolicitado alquilerSolicitado(UUID idCliente) {
        return new DatosAlquilerSolicitado(UUID.randomUUID(), idCliente, ContactoFixture.NOMBRE, "1234567K",
                ContactoFixture.SUCURSAL_XELA, "Quetzaltenango", "Antigua Guatemala", SALIDA, SALIDA.plusDays(2), 30,
                new BigDecimal("3500.00"));
    }

    public static DatosAlquilerCotizado alquilerCotizado(UUID idCliente) {
        return new DatosAlquilerCotizado(UUID.randomUUID(), idCliente, new BigDecimal("3800.00"),
                SALIDA.plusDays(2).plusHours(2), UUID.randomUUID());
    }

    public static DatosAlquilerConfirmado alquilerConfirmado(UUID idCliente) {
        return new DatosAlquilerConfirmado(UUID.randomUUID(), idCliente, ContactoFixture.NOMBRE, "1234567K",
                ContactoFixture.SUCURSAL_XELA, "Quetzaltenango", "Antigua Guatemala", SALIDA, SALIDA.plusDays(2),
                SALIDA.plusDays(2).plusHours(2), true, 30, new BigDecimal("3800.00"), UUID.randomUUID(),
                LocalDate.parse("2026-10-20"));
    }

    public static DatosAlquilerCancelado alquilerCancelado(UUID idCliente, UUID idPago) {
        return new DatosAlquilerCancelado(UUID.randomUUID(), idCliente, ContactoFixture.NOMBRE, "1234567K",
                ContactoFixture.SUCURSAL_XELA, new BigDecimal("3800.00"), idPago, "VIAJE_ELIMINADO",
                Instant.parse("2026-10-22T09:15:00Z"));
    }

    public static DatosEnlaceContrasena enlace(PropositoContrasena proposito) {
        return new DatosEnlaceContrasena(UUID.randomUUID(), "ana.lopez@gmail.com", "Ana López", proposito, TOKEN,
                Instant.parse("2026-10-20T16:30:00Z"));
    }

    public static DatosContrasenaCambiada contrasenaCambiada(MotivoContrasena motivo) {
        return new DatosContrasenaCambiada(UUID.randomUUID(), "ana.lopez@gmail.com", "Ana López", motivo,
                Instant.parse("2026-10-20T15:12:00Z"));
    }
}
