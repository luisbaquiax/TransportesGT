package org.transportsgt.customerwalletservice.fixture;

import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.boletos.Canal;
import org.transportsgt.eventos.payload.alquileres.DatosAlquilerCancelado;
import org.transportsgt.eventos.payload.boletos.DatosBoletosAnulados;
import org.transportsgt.eventos.payload.identidad.DatosUsuario;
import org.transportsgt.eventos.payload.identidad.Rol;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class EventoFixture {

    /** 05:30 UTC del 22 = 23:30 del 21 en Guatemala (UTC-6): prueba la zona horaria. */
    public static final Instant OCURRIDO_EN = Instant.parse("2026-10-22T05:30:00Z");
    public static final LocalDate FECHA_GUATEMALA = LocalDate.parse("2026-10-21");
    public static final String MOTIVO = "VIAJE_ELIMINADO";

    private EventoFixture() {
    }

    public static <T> SobreEvento<T> sobre(TipoEvento tipo, T datos, Long versionAgregado) {
        return new SobreEvento<>(UUID.randomUUID(), tipo.nombre(), 1, OCURRIDO_EN,
                tipo.productor().nombreServicio(), UUID.randomUUID(), versionAgregado, UUID.randomUUID(), datos);
    }

    public static SobreEvento<Object> sinFecha(TipoEvento tipo, Object datos) {
        return new SobreEvento<>(UUID.randomUUID(), tipo.nombre(), 1, null,
                tipo.productor().nombreServicio(), UUID.randomUUID(), 0L, UUID.randomUUID(), datos);
    }

    public static DatosUsuario usuario(UUID idUsuario, String nombre, boolean activo) {
        return new DatosUsuario(idUsuario, ClienteFixture.CORREO, nombre, Rol.CLIENTE, null, activo);
    }

    public static DatosUsuario usuario(UUID idUsuario) {
        return usuario(idUsuario, ClienteFixture.NOMBRE, true);
    }

    public static DatosBoletosAnulados boletosAnulados(UUID idCompra, Canal canal, UUID idPago) {
        return new DatosBoletosAnulados(idCompra, UUID.randomUUID(), MOTIVO, canal, new BigDecimal("170.00"),
                UUID.randomUUID(), idPago, UUID.randomUUID());
    }

    public static DatosAlquilerCancelado alquilerCancelado(UUID idAlquiler, UUID idPago, Instant canceladoEn) {
        return new DatosAlquilerCancelado(idAlquiler, UUID.randomUUID(), ClienteFixture.NOMBRE, ClienteFixture.NIT,
                UUID.randomUUID(), new BigDecimal("3800.00"), idPago, MOTIVO, canceladoEn);
    }
}
