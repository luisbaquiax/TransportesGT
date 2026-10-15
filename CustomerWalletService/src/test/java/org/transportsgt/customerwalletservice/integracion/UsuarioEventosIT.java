package org.transportsgt.customerwalletservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosUsuario;
import org.transportsgt.eventos.payload.Rol;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Consumidor de {@code cartera.identidad.q}: UsuarioCreado / UsuarioActualizado y PerfilClienteActualizado en el outbox.
 */
class UsuarioEventosIT extends IntegracionBase {

    private DatosUsuario usuario(UUID id, String nombre, boolean activo) {
        return new DatosUsuario(id, "u." + id + "@transportes.gt", nombre, Rol.CLIENTE, null, activo);
    }

    @Test
    void usuarioCreado_cuandoLlegaDuplicado_creaUnSoloClienteYUnaCartera() {
        // Arrange
        UUID idUsuario = UUID.randomUUID();
        SobreEvento<DatosUsuario> evento = sobre(TipoEvento.USUARIO_CREADO, idUsuario, 0L,
                usuario(idUsuario, "Ana López", true));
        // El mismo usuario con otro idEvento (Identidad republicó)
        SobreEvento<DatosUsuario> republicado = sobre(TipoEvento.USUARIO_CREADO, idUsuario, 0L,
                usuario(idUsuario, "Ana López", true));

        // Act
        publicar(TipoEvento.USUARIO_CREADO, evento);
        publicar(TipoEvento.USUARIO_CREADO, evento);
        publicar(TipoEvento.USUARIO_CREADO, republicado);
        esperar("los tres mensajes procesados", () -> eventoProcesado(evento.idEvento())
                && eventoProcesado(republicado.idEvento()));

        // Assert
        assertAll(
                () -> assertEquals(1, clientes(idUsuario)),
                () -> assertEquals(1, jdbc.queryForObject("""
                        SELECT count(*) FROM cartera ca JOIN cliente c ON c.id = ca.id_cliente WHERE c.id_usuario = ?
                        """, Integer.class, idUsuario)),
                () -> assertEquals(0, saldo(idUsuario).signum()),
                () -> assertEquals(1, jdbc.queryForObject("""
                        SELECT count(*) FROM evento_procesado WHERE id_evento = ? AND consumidor = 'cartera'
                        """, Integer.class, evento.idEvento()))
        );
    }

    @Test
    void usuarioCreado_publicaPerfilClienteActualizadoEnElOutboxConLaLlaveCorrecta() {
        // Arrange
        UUID idUsuario = UUID.randomUUID();

        // Act
        publicarUsuarioCreado(idUsuario, true);

        // Assert
        Map<String, Object> fila = jdbc.queryForMap("""
                SELECT llave_ruteo, tipo_agregado, version_agregado, datos ->> 'nit' AS nit,
                       datos ->> 'idUsuario' AS id_usuario, (datos ->> 'activo')::boolean AS activo
                  FROM outbox_evento WHERE tipo_evento = 'PerfilClienteActualizado' AND id_agregado = ?
                """, idUsuario);
        assertAll(
                () -> assertEquals("cartera.cliente.actualizado", fila.get("llave_ruteo")),
                () -> assertEquals("cliente", fila.get("tipo_agregado")),
                () -> assertEquals(0L, fila.get("version_agregado")),
                () -> assertEquals(null, fila.get("nit")),
                () -> assertEquals(idUsuario.toString(), fila.get("id_usuario")),
                () -> assertEquals(true, fila.get("activo"))
        );
        esperar("outbox publicado", () -> jdbc.queryForObject("""
                SELECT count(*) FROM outbox_evento WHERE id_agregado = ? AND publicado_en IS NOT NULL
                """, Integer.class, idUsuario) == 1);
    }

    @Test
    void usuarioActualizado_cuandoTraeVersionVieja_seIgnora() {
        // Arrange
        UUID idUsuario = UUID.randomUUID();
        SobreEvento<DatosUsuario> nuevo = sobre(TipoEvento.USUARIO_ACTUALIZADO, idUsuario, 5L,
                usuario(idUsuario, "Nombre v5", true));
        SobreEvento<DatosUsuario> viejo = sobre(TipoEvento.USUARIO_ACTUALIZADO, idUsuario, 3L,
                usuario(idUsuario, "Nombre v3", false));

        // Act
        publicar(TipoEvento.USUARIO_ACTUALIZADO, nuevo);
        esperar("v5 procesado", () -> eventoProcesado(nuevo.idEvento()));
        publicar(TipoEvento.USUARIO_ACTUALIZADO, viejo);
        esperar("v3 procesado", () -> eventoProcesado(viejo.idEvento()));

        // Assert
        Map<String, Object> cliente = jdbc.queryForMap(
                "SELECT nombre_completo, activo, version_usuario FROM cliente WHERE id_usuario = ?", idUsuario);
        assertAll(
                () -> assertEquals("Nombre v5", cliente.get("nombre_completo")),
                () -> assertEquals(true, cliente.get("activo")),
                () -> assertEquals(5L, cliente.get("version_usuario"))
        );
    }

    @Test
    void usuarioActualizado_cuandoLlegaAntesQueCreado_creaElClienteYDescartaElCreado() {
        // Arrange
        UUID idUsuario = UUID.randomUUID();
        SobreEvento<DatosUsuario> actualizado = sobre(TipoEvento.USUARIO_ACTUALIZADO, idUsuario, 1L,
                usuario(idUsuario, "Nombre corregido", true));
        SobreEvento<DatosUsuario> creado = sobre(TipoEvento.USUARIO_CREADO, idUsuario, 0L,
                usuario(idUsuario, "Nombre original", true));

        // Act
        publicar(TipoEvento.USUARIO_ACTUALIZADO, actualizado);
        esperar("actualizado procesado", () -> eventoProcesado(actualizado.idEvento()));
        publicar(TipoEvento.USUARIO_CREADO, creado);
        esperar("creado procesado", () -> eventoProcesado(creado.idEvento()));

        // Assert
        assertAll(
                () -> assertEquals(1, clientes(idUsuario)),
                () -> assertEquals("Nombre corregido", jdbc.queryForObject(
                        "SELECT nombre_completo FROM cliente WHERE id_usuario = ?", String.class, idUsuario)),
                () -> assertEquals(1L, jdbc.queryForObject(
                        "SELECT version_usuario FROM cliente WHERE id_usuario = ?", Long.class, idUsuario))
        );
    }

    @Test
    void usuarioActualizado_cuandoDesactivaAlUsuario_publicaPerfilConActivoFalse() {
        // Arrange
        UUID idUsuario = UUID.randomUUID();
        publicarUsuarioCreado(idUsuario, true);
        SobreEvento<DatosUsuario> desactivado = sobre(TipoEvento.USUARIO_ACTUALIZADO, idUsuario, 1L,
                usuario(idUsuario, "Cliente " + idUsuario.toString().substring(0, 8), false));

        // Act
        publicar(TipoEvento.USUARIO_ACTUALIZADO, desactivado);
        esperar("desactivación procesada", () -> eventoProcesado(desactivado.idEvento()));

        // Assert
        assertEquals(2, jdbc.queryForObject("""
                SELECT count(*) FROM outbox_evento WHERE tipo_evento = 'PerfilClienteActualizado' AND id_agregado = ?
                """, Integer.class, idUsuario));
        assertEquals("false", jdbc.queryForObject("""
                SELECT datos ->> 'activo' FROM outbox_evento
                 WHERE tipo_evento = 'PerfilClienteActualizado' AND id_agregado = ? ORDER BY version_agregado DESC LIMIT 1
                """, String.class, idUsuario));
    }

    @Test
    void mensajeInvalido_vaDirectoALaDlq() {
        // Arrange
        String cuerpo = "{ esto no es JSON " + UUID.randomUUID();

        // Act
        publicarCrudo(TipoEvento.USUARIO_CREADO.llaveRuteo(), cuerpo.getBytes(StandardCharsets.UTF_8));

        // Assert
        Message enDlq = rabbit.receive("cartera.identidad.dlq", 15_000);
        assertNotNull(enDlq, "El mensaje inválido debía llegar a cartera.identidad.dlq");
        assertAll(
                () -> assertEquals(cuerpo, new String(enDlq.getBody(), StandardCharsets.UTF_8)),
                () -> assertTrue(enDlq.getMessageProperties().getHeader("x-motivo-fallo").toString()
                        .startsWith("JSON inválido"))
        );
    }
}
