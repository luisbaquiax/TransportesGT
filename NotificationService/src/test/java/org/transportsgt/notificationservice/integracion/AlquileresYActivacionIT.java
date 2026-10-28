package org.transportsgt.notificationservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.transportsgt.notificationservice.services.licencia.AvisosLicenciaService;
import org.transportsgt.notificationservice.utils.Fechas;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.alquileres.DatosAlquilerConfirmado;
import org.transportsgt.eventos.payload.alquileres.DatosAlquilerCotizado;
import org.transportsgt.eventos.payload.flota.DatosChofer;
import org.transportsgt.eventos.payload.flota.TipoLicencia;
import org.transportsgt.eventos.payload.identidad.DatosEnlaceContrasena;
import org.transportsgt.eventos.payload.identidad.DatosUsuario;
import org.transportsgt.eventos.payload.identidad.PropositoContrasena;
import org.transportsgt.eventos.payload.identidad.Rol;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Correos de cotización y confirmación de alquileres (con y sin cuenta) y el enlace de activación.
 */
class AlquileresYActivacionIT extends IntegracionBase {

    private static final OffsetDateTime SALIDA = OffsetDateTime.parse("2026-11-20T07:00:00-06:00");

    @Autowired
    private AvisosLicenciaService avisosLicencia;

    @Test
    void cotizacionConYSinCuenta() {
        DatosUsuario cliente = contacto(Rol.CLIENTE, null, true);
        var conCuenta = sobre(TipoEvento.ALQUILER_COTIZADO, UUID.randomUUID(), 2L, new DatosAlquilerCotizado(
                UUID.randomUUID(), cliente.idUsuario(), new BigDecimal("2700.00"), SALIDA.plusHours(4), UUID.randomUUID()));
        var sinCuenta = sobre(TipoEvento.ALQUILER_COTIZADO, UUID.randomUUID(), 2L, new DatosAlquilerCotizado(
                UUID.randomUUID(), null, new BigDecimal("2700.00"), null, UUID.randomUUID()));

        publicarYEsperar(TipoEvento.ALQUILER_COTIZADO, conCuenta);
        publicarYEsperar(TipoEvento.ALQUILER_COTIZADO, sinCuenta);
        enviarPendientes();

        assertEquals(1, notificaciones(conCuenta.idEvento()));
        assertEquals(0, notificaciones(sinCuenta.idEvento()));
    }

    @Test
    void confirmacionAvisaAlClienteYALosAdministradoresDeLaSucursal() {
        UUID sucursal = UUID.randomUUID();
        DatosUsuario admin = contacto(Rol.ADMIN_SUCURSAL, sucursal, true);
        DatosUsuario cliente = contacto(Rol.CLIENTE, null, true);
        var conCuenta = sobre(TipoEvento.ALQUILER_CONFIRMADO, UUID.randomUUID(), 3L, confirmado(cliente.idUsuario(), sucursal));
        var mostrador = sobre(TipoEvento.ALQUILER_CONFIRMADO, UUID.randomUUID(), 3L, confirmado(null, sucursal));

        publicarYEsperar(TipoEvento.ALQUILER_CONFIRMADO, conCuenta);
        publicarYEsperar(TipoEvento.ALQUILER_CONFIRMADO, mostrador);
        enviarPendientes();

        assertEquals(2, notificaciones(conCuenta.idEvento()));
        assertEquals(1, notificaciones(mostrador.idEvento()));
        assertTrue(recibidosPor(admin.correo()).size() >= 2);
    }

    @Test
    void enlaceDeActivacion() {
        String correo = "activar." + UUID.randomUUID().toString().substring(0, 8) + "@transportes.gt";
        DatosEnlaceContrasena datos = new DatosEnlaceContrasena(UUID.randomUUID(), correo, "Nuevo Usuario",
                PropositoContrasena.ACTIVACION, "token-" + UUID.randomUUID(), Instant.now().plus(1, ChronoUnit.DAYS));
        var evento = sobre(TipoEvento.ENLACE_CONTRASENA_EMITIDO, datos.idUsuario(), null, datos);

        publicarYEsperar(TipoEvento.ENLACE_CONTRASENA_EMITIDO, evento);

        esperar("correo de activación", () -> recibidosPor(correo).size() == 1);
    }

    @Test
    void choferSinVersionYVersionVieja() {
        UUID idChofer = UUID.randomUUID();
        UUID sucursal = UUID.randomUUID();
        var sinVersion = sobre(TipoEvento.CHOFER_REGISTRADO, idChofer, null, chofer(idChofer, sucursal, 90, true));
        var nueva = sobre(TipoEvento.CHOFER_ACTUALIZADO, idChofer, 5L, chofer(idChofer, sucursal, 120, true));
        var vieja = sobre(TipoEvento.CHOFER_ACTUALIZADO, idChofer, 3L, chofer(idChofer, sucursal, 30, false));

        publicarYEsperar(TipoEvento.CHOFER_REGISTRADO, sinVersion);
        publicarYEsperar(TipoEvento.CHOFER_ACTUALIZADO, nueva);
        publicarYEsperar(TipoEvento.CHOFER_ACTUALIZADO, vieja);

        assertEquals(5L, jdbc.queryForObject("SELECT version FROM chofer_licencia WHERE id_chofer = ?",
                Long.class, idChofer));
    }

    @Test
    void avisoDeHoyDeChoferInactivoOConOtraFechaSeCancela() {
        UUID sucursal = UUID.randomUUID();
        UUID inactivo = UUID.randomUUID();
        UUID otraFecha = UUID.randomUUID();
        long dias = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(),
                LocalDate.now(Fechas.ZONA_GUATEMALA).plusDays(15));
        publicarYEsperar(TipoEvento.CHOFER_REGISTRADO,
                sobre(TipoEvento.CHOFER_REGISTRADO, inactivo, 0L, chofer(inactivo, sucursal, (int) dias, true)));
        publicarYEsperar(TipoEvento.CHOFER_REGISTRADO,
                sobre(TipoEvento.CHOFER_REGISTRADO, otraFecha, 0L, chofer(otraFecha, sucursal, (int) dias, true)));
        // Cambios en la proyección sin pasar por el evento: el aviso de hoy queda desfasado
        jdbc.update("UPDATE chofer_licencia SET activo = false WHERE id_chofer = ?", inactivo);
        jdbc.update("UPDATE chofer_licencia SET fecha_vencimiento_licencia = fecha_vencimiento_licencia + 1 "
                + "WHERE id_chofer = ?", otraFecha);

        avisosLicencia.procesar();

        for (UUID id : new UUID[]{inactivo, otraFecha}) {
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM recordatorio_licencia WHERE id_chofer = ? "
                    + "AND estado = 'CANCELADO'", Integer.class, id));
        }
    }

    private static DatosChofer chofer(UUID idChofer, UUID sucursal, int diasParaVencer, boolean activo) {
        return new DatosChofer(idChofer, null, sucursal, "Pedro Chofer", "chofer." + idChofer.toString().substring(0, 8)
                + "@transportes.gt", "55551234", "LIC-9", TipoLicencia.A, LocalDate.now().plusDays(diasParaVencer),
                new BigDecimal("150.00"), activo);
    }

    private static DatosAlquilerConfirmado confirmado(UUID idCliente, UUID sucursal) {
        return new DatosAlquilerConfirmado(UUID.randomUUID(), idCliente, "Cliente Mostrador", "1234567K", sucursal,
                "Xela", "Antigua", SALIDA, SALIDA.plusDays(1), SALIDA.plusHours(4), true, 30,
                new BigDecimal("2700.00"), idCliente == null ? null : UUID.randomUUID(), LocalDate.parse("2026-10-20"));
    }
}
