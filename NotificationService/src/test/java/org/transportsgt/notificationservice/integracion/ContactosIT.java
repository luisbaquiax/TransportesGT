package org.transportsgt.notificationservice.integracion;

import org.junit.jupiter.api.Test;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.cartera.DatosCarteraRecargada;
import org.transportsgt.eventos.payload.identidad.DatosUsuario;
import org.transportsgt.eventos.payload.identidad.Rol;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proyección de contactos (UsuarioCreado / UsuarioActualizado) e idempotencia del consumidor.
 */
class ContactosIT extends IntegracionBase {

    private Map<String, Object> fila(UUID idUsuario) {
        return jdbc.queryForMap("SELECT correo, nombre_completo, rol, activo, version FROM contacto WHERE id_usuario = ?",
                idUsuario);
    }

    @Test
    void usuarioCreado_cuandoEsNuevo_creaElContacto() {
        // Act
        DatosUsuario usuario = contacto(Rol.CLIENTE, null, true);

        // Assert
        Map<String, Object> fila = fila(usuario.idUsuario());
        assertAll(
                () -> assertEquals(usuario.correo(), fila.get("correo")),
                () -> assertEquals("CLIENTE", fila.get("rol")),
                () -> assertEquals(true, fila.get("activo")),
                () -> assertEquals(0L, fila.get("version"))
        );
    }

    @Test
    void usuarioActualizado_cuandoTraeVersionVieja_seIgnora() {
        // Arrange
        UUID idUsuario = UUID.randomUUID();
        DatosUsuario v5 = new DatosUsuario(idUsuario, "nuevo@transportes.gt", "Nombre v5", Rol.CAJERO, UUID.randomUUID(), true);
        DatosUsuario v3 = new DatosUsuario(idUsuario, "viejo@transportes.gt", "Nombre v3", Rol.CAJERO, v5.idSucursal(), false);

        // Act: Actualizado v5 llega antes que el v3 (y que el Creado)
        publicarYEsperar(TipoEvento.USUARIO_ACTUALIZADO, sobre(TipoEvento.USUARIO_ACTUALIZADO, idUsuario, 5L, v5));
        publicarYEsperar(TipoEvento.USUARIO_ACTUALIZADO, sobre(TipoEvento.USUARIO_ACTUALIZADO, idUsuario, 3L, v3));

        // Assert
        Map<String, Object> fila = fila(idUsuario);
        assertAll(
                () -> assertEquals("nuevo@transportes.gt", fila.get("correo")),
                () -> assertEquals(true, fila.get("activo")),
                () -> assertEquals(5L, fila.get("version"))
        );
    }

    @Test
    void carteraRecargada_cuandoLlegaDuplicada_generaUnaSolaNotificacionYUnCorreo() {
        // Arrange
        DatosUsuario cliente = contacto(Rol.CLIENTE, null, true);
        SobreEvento<DatosCarteraRecargada> evento = sobre(TipoEvento.CARTERA_RECARGADA, UUID.randomUUID(), 1L,
                new DatosCarteraRecargada(UUID.randomUUID(), UUID.randomUUID(), cliente.idUsuario(),
                        new BigDecimal("200.00"), new BigDecimal("350.00"), LocalDate.parse("2026-10-20")));

        // Act
        publicar(TipoEvento.CARTERA_RECARGADA, evento);
        publicar(TipoEvento.CARTERA_RECARGADA, evento);
        publicarYEsperar(TipoEvento.CARTERA_RECARGADA, evento);
        enviarPendientes();
        enviarPendientes();

        // Assert
        assertAll(
                () -> assertEquals(1, notificaciones(evento.idEvento())),
                () -> assertEquals(1, recibidosPor(cliente.correo()).size())
        );
    }
}
