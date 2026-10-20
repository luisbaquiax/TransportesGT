package org.transportsgt.notificationservice.integracion;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosChofer;
import org.transportsgt.eventos.payload.DatosUsuario;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.eventos.payload.TipoLicencia;
import org.transportsgt.notificationservice.services.licencia.AvisosLicenciaService;
import org.transportsgt.notificationservice.utils.Fechas;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Avisos de vencimiento de licencia: programación al consumir ChoferRegistrado/Actualizado, job diario y consulta.
 */
class LicenciasIT extends IntegracionBase {

    @Autowired
    private AvisosLicenciaService avisosLicencia;

    private LocalDate hoy() {
        return LocalDate.now(Fechas.ZONA_GUATEMALA);
    }

    private DatosChofer chofer(UUID idChofer, UUID idSucursal, LocalDate vencimiento, boolean activo) {
        return new DatosChofer(idChofer, null, idSucursal, "Juan Pérez", "chofer." + idChofer.toString().substring(0, 8)
                + "@transportes.gt", "55551234", "LIC-1", TipoLicencia.A, vencimiento, new BigDecimal("150.00"), activo);
    }

    private void publicarChofer(TipoEvento tipo, DatosChofer datos, long version) {
        publicarYEsperar(tipo, sobre(tipo, datos.idChofer(), version, datos));
    }

    private List<Map<String, Object>> avisos(UUID idChofer) {
        return jdbc.queryForList("""
                SELECT fecha_vencimiento, dias_anticipacion, fecha_envio_programada, estado
                  FROM recordatorio_licencia WHERE id_chofer = ? ORDER BY fecha_vencimiento, dias_anticipacion DESC
                """, idChofer);
    }

    private long pendientes(UUID idChofer) {
        return avisos(idChofer).stream().filter(a -> "PENDIENTE".equals(a.get("estado"))).count();
    }

    @Test
    void choferRegistrado_cuandoAlgunAvisoYaPaso_programaSoloLosQueFaltan() {
        // Arrange
        UUID lejos = UUID.randomUUID();
        UUID cerca = UUID.randomUUID();

        // Act
        publicarChofer(TipoEvento.CHOFER_REGISTRADO, chofer(lejos, UUID.randomUUID(), hoy().plusDays(60), true), 0L);
        publicarChofer(TipoEvento.CHOFER_REGISTRADO, chofer(cerca, UUID.randomUUID(), hoy().plusDays(20), true), 0L);

        // Assert
        assertAll(
                () -> assertEquals(List.of(30, 15, 3),
                        avisos(lejos).stream().map(a -> a.get("dias_anticipacion")).toList()),
                () -> assertEquals(java.sql.Date.valueOf(hoy().plusDays(30)), avisos(lejos).getFirst().get("fecha_envio_programada")),
                () -> assertEquals(List.of(15, 3),
                        avisos(cerca).stream().map(a -> a.get("dias_anticipacion")).toList())
        );
    }

    @Test
    void choferActualizado_conOtraFecha_cancelaLosViejosYCreaNuevos() {
        // Arrange
        UUID idChofer = UUID.randomUUID();
        UUID sucursal = UUID.randomUUID();
        publicarChofer(TipoEvento.CHOFER_REGISTRADO, chofer(idChofer, sucursal, hoy().plusDays(60), true), 0L);

        // Act
        publicarChofer(TipoEvento.CHOFER_ACTUALIZADO, chofer(idChofer, sucursal, hoy().plusDays(400), true), 1L);

        // Assert
        List<Map<String, Object>> todos = avisos(idChofer);
        assertAll(
                () -> assertEquals(6, todos.size()),
                () -> assertTrue(todos.subList(0, 3).stream().allMatch(a -> "CANCELADO".equals(a.get("estado")))),
                () -> assertTrue(todos.subList(3, 6).stream().allMatch(a -> "PENDIENTE".equals(a.get("estado"))))
        );
    }

    @Test
    void choferActualizado_cuandoSeDesactiva_cancelaTodosSusAvisos() {
        // Arrange
        UUID idChofer = UUID.randomUUID();
        UUID sucursal = UUID.randomUUID();
        publicarChofer(TipoEvento.CHOFER_REGISTRADO, chofer(idChofer, sucursal, hoy().plusDays(60), true), 0L);

        // Act
        publicarChofer(TipoEvento.CHOFER_ACTUALIZADO, chofer(idChofer, sucursal, hoy().plusDays(60), false), 1L);

        // Assert
        assertAll(
                () -> assertEquals(3, avisos(idChofer).size()),
                () -> assertEquals(0, pendientes(idChofer))
        );
    }

    @Test
    void procesarLicencias_cuandoHayAvisoDeHoy_avisaAlChoferYACadaAdminActivo() throws Exception {
        // Arrange
        UUID sucursal = UUID.randomUUID();
        DatosUsuario admin1 = contacto(Rol.ADMIN_SUCURSAL, sucursal, true);
        DatosUsuario admin2 = contacto(Rol.ADMIN_SUCURSAL, sucursal, true);
        DatosUsuario inactivo = contacto(Rol.ADMIN_SUCURSAL, sucursal, false);
        DatosUsuario deOtra = contacto(Rol.ADMIN_SUCURSAL, UUID.randomUUID(), true);
        DatosChofer datos = chofer(UUID.randomUUID(), sucursal, hoy().plusDays(15), true);
        publicarChofer(TipoEvento.CHOFER_REGISTRADO, datos, 0L);

        // Act: el aviso de 15 días es para hoy
        avisosLicencia.procesar();
        enviarPendientes();

        // Assert
        assertAll(
                () -> assertEquals(List.of("ENVIADO", "PENDIENTE"),
                        avisos(datos.idChofer()).stream().map(a -> a.get("estado")).toList()),
                () -> assertEquals(1, recibidosPor(datos.correo()).size()),
                () -> assertEquals(1, recibidosPor(admin1.correo()).size()),
                () -> assertEquals(1, recibidosPor(admin2.correo()).size()),
                () -> assertEquals(0, recibidosPor(inactivo.correo()).size()),
                () -> assertEquals(0, recibidosPor(deOtra.correo()).size())
        );
        MimeMessage alChofer = recibidosPor(datos.correo()).getFirst();
        assertAll(
                () -> assertEquals("Licencia por vencer en 15 días", alChofer.getSubject()),
                () -> assertTrue(contenido(alChofer).contains("Tu licencia de conducir vence en 15 días"))
        );
        // Una segunda corrida el mismo día no repite el aviso
        avisosLicencia.procesar();
        enviarPendientes();
        assertEquals(1, recibidosPor(datos.correo()).size());
    }

    @Test
    void licencias_comoAdminDeSucursal_veSoloLaSuyaYOtraEsProhibida() throws Exception {
        // Arrange
        UUID sucursal = UUID.randomUUID();
        publicarChofer(TipoEvento.CHOFER_REGISTRADO, chofer(UUID.randomUUID(), sucursal, hoy().plusDays(60), true), 0L);
        String admin = token(Rol.ADMIN_SUCURSAL, sucursal);

        // Act & Assert
        mockMvc.perform(conToken(get("/notificaciones/licencias"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.contenido[0].nombreChofer").value("Juan Pérez"))
                .andExpect(jsonPath("$.contenido[0].diasAnticipacion").value(30));
        mockMvc.perform(conToken(get("/notificaciones/licencias").param("idSucursal", UUID.randomUUID().toString()), admin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        mockMvc.perform(conToken(get("/notificaciones/licencias").param("idSucursal", sucursal.toString())
                        .param("estado", "PENDIENTE"), tokenSistema()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(3));
        mockMvc.perform(conToken(get("/notificaciones/licencias"), token(Rol.CAJERO, sucursal)))
                .andExpect(status().isForbidden());
    }
}
