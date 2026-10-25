package org.transportsgt.notificationservice.controllers;

import org.transportsgt.notificationservice.config.security.ConvertidorUsuarioAutenticado;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.notificationservice.config.security.UsuarioAutenticado;
import org.transportsgt.notificationservice.dto.response.NotificacionDetalleResponse;
import org.transportsgt.notificationservice.dto.response.NotificacionResumenResponse;
import org.transportsgt.notificationservice.dto.response.PaginaResponse;
import org.transportsgt.notificationservice.dto.response.ProcesamientoResponse;
import org.transportsgt.notificationservice.dto.response.RecordatorioLicenciaResponse;
import org.transportsgt.notificationservice.fixture.ContactoFixture;
import org.transportsgt.notificationservice.models.EstadoNotificacion;
import org.transportsgt.notificationservice.models.EstadoRecordatorio;
import org.transportsgt.notificationservice.models.TipoNotificacion;
import org.transportsgt.notificationservice.services.bitacora.BitacoraService;
import org.transportsgt.notificationservice.services.bitacora.ConsultaLicenciasService;
import org.transportsgt.notificationservice.services.envio.EnvioCorreosService;
import org.transportsgt.notificationservice.services.envio.ResumenEnvio;
import org.transportsgt.notificationservice.services.licencia.AvisosLicenciaService;
import org.transportsgt.notificationservice.services.licencia.ResumenAvisos;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ControladoresTest {

    @Mock
    private BitacoraService bitacora;
    @Mock
    private ConsultaLicenciasService consultaLicencias;
    @Mock
    private AvisosLicenciaService avisos;
    @Mock
    private EnvioCorreosService envio;

    @Test
    void bitacora_cuandoSeConsultaOReintenta_delegaEnElServicio() {
        // Arrange
        NotificacionController controlador = new NotificacionController(bitacora);
        UUID id = UUID.randomUUID();
        LocalDate dia = LocalDate.parse("2026-10-20");
        PaginaResponse<NotificacionResumenResponse> pagina = new PaginaResponse<>(List.of(), 0, 20, 0, 0);
        NotificacionDetalleResponse detalle = mock(NotificacionDetalleResponse.class);
        NotificacionResumenResponse resumen = mock(NotificacionResumenResponse.class);
        when(bitacora.listar(EstadoNotificacion.FALLIDA, TipoNotificacion.COMPRA_BOLETOS, dia, dia, 0, 20)).thenReturn(pagina);
        when(bitacora.detalle(id)).thenReturn(detalle);
        when(bitacora.reintentar(id)).thenReturn(resumen);

        // Act & Assert
        assertAll(
                () -> assertSame(pagina, controlador.listar(EstadoNotificacion.FALLIDA, TipoNotificacion.COMPRA_BOLETOS,
                        dia, dia, 0, 20)),
                () -> assertSame(detalle, controlador.detalle(id)),
                () -> assertSame(resumen, controlador.reintentar(id))
        );
    }

    @Test
    void listarLicencias_cuandoLlamaUnAdmin_usaElUsuarioDelToken() {
        // Arrange
        LicenciaController controlador = new LicenciaController(new ConvertidorUsuarioAutenticado(), consultaLicencias);
        PaginaResponse<RecordatorioLicenciaResponse> pagina = new PaginaResponse<>(List.of(), 0, 20, 0, 0);
        ArgumentCaptor<UsuarioAutenticado> usuario = ArgumentCaptor.forClass(UsuarioAutenticado.class);
        when(consultaLicencias.listar(usuario.capture(), eq(ContactoFixture.SUCURSAL_XELA), eq(EstadoRecordatorio.PENDIENTE),
                eq(0), eq(20))).thenReturn(pagina);

        // Act
        PaginaResponse<RecordatorioLicenciaResponse> respuesta = controlador.listar(ContactoFixture.SUCURSAL_XELA,
                EstadoRecordatorio.PENDIENTE, 0, 20, ContactoFixture.jwt(Rol.ADMIN_SUCURSAL, ContactoFixture.SUCURSAL_XELA));

        // Assert
        assertAll(
                () -> assertSame(pagina, respuesta),
                () -> assertEquals(Rol.ADMIN_SUCURSAL, usuario.getValue().rol()),
                () -> assertEquals(ContactoFixture.SUCURSAL_XELA, usuario.getValue().idSucursal())
        );
    }

    @Test
    void procesar_cuandoSeLlama_ejecutaPrimeroLicenciasYLuegoElEnvio() {
        // Arrange
        when(avisos.procesar()).thenReturn(new ResumenAvisos(1, 0, 0));
        when(envio.procesar()).thenReturn(new ResumenEnvio(3, 0, 0));

        // Act
        ProcesamientoResponse respuesta = new ProcesamientoController(avisos, envio).procesar();

        // Assert
        assertEquals(new ProcesamientoResponse(new ResumenAvisos(1, 0, 0), new ResumenEnvio(3, 0, 0)), respuesta);
        var orden = org.mockito.Mockito.inOrder(avisos, envio);
        orden.verify(avisos).procesar();
        orden.verify(envio).procesar();
    }
}
