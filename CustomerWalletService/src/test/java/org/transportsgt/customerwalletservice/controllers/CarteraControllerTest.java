package org.transportsgt.customerwalletservice.controllers;

import org.transportsgt.customerwalletservice.config.security.ConvertidorUsuarioAutenticado;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.transportsgt.customerwalletservice.config.security.FiltroClaveInterna;
import org.transportsgt.customerwalletservice.config.security.UsuarioAutenticado;
import org.transportsgt.customerwalletservice.dto.request.PagoRequest;
import org.transportsgt.customerwalletservice.dto.request.RecargaRequest;
import org.transportsgt.customerwalletservice.dto.response.CarteraResponse;
import org.transportsgt.customerwalletservice.dto.response.MovimientoResponse;
import org.transportsgt.customerwalletservice.dto.response.PagoConsultaResponse;
import org.transportsgt.customerwalletservice.dto.response.PagoResponse;
import org.transportsgt.customerwalletservice.dto.response.PaginaResponse;
import org.transportsgt.customerwalletservice.dto.response.Resultado;
import org.transportsgt.customerwalletservice.fixture.ClienteFixture;
import org.transportsgt.customerwalletservice.models.TipoMovimiento;
import org.transportsgt.customerwalletservice.services.CarteraService;
import org.transportsgt.customerwalletservice.services.PagoService;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.eventos.payload.TipoReferencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CarteraControllerTest {

    private static final LocalDate FECHA = LocalDate.parse("2026-10-20");

    @Mock
    private CarteraService carteraService;
    @Mock
    private PagoService pagoService;

    private CarteraController controlador;
    private UUID idUsuario;
    private Jwt jwt;
    private UUID idReferencia;
    private MovimientoResponse movimiento;
    private PagoResponse pago;

    @BeforeEach
    void setUp() {
        controlador = new CarteraController(new ConvertidorUsuarioAutenticado(), carteraService, pagoService);
        idUsuario = UUID.randomUUID();
        jwt = ClienteFixture.jwt(idUsuario, Rol.CLIENTE);
        idReferencia = UUID.randomUUID();
        movimiento = new MovimientoResponse(UUID.randomUUID(), TipoMovimiento.RECARGA, new BigDecimal("200.00"),
                new BigDecimal("200.00"), null, null, FECHA, "Recarga de saldo", null);
        pago = new PagoResponse(UUID.randomUUID(), new BigDecimal("30.00"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
                new MockHttpServletRequest("POST", "/v1/customers/cartera/pagos")));
    }

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Nested
    class Obtener {

        @Test
        void obtener_delegaConElUsuarioDelToken() {
            // Arrange
            CarteraResponse cartera = new CarteraResponse(UUID.randomUUID(), new BigDecimal("1.00"), null);
            when(carteraService.obtener(any())).thenReturn(cartera);

            // Act & Assert
            assertSame(cartera, controlador.obtener(jwt));
        }
    }

    @Nested
    class Recargar {

        @Test
        void recargar_cuandoEsNueva_responde201() {
            // Arrange
            RecargaRequest request = new RecargaRequest(new BigDecimal("200.00"), FECHA);
            when(carteraService.recargar(any(), eq("clave"), eq(request))).thenReturn(Resultado.nuevo(movimiento));

            // Act
            ResponseEntity<MovimientoResponse> respuesta = controlador.recargar("clave", request, jwt);

            // Assert
            assertAll(
                    () -> assertEquals(HttpStatus.CREATED, respuesta.getStatusCode()),
                    () -> assertSame(movimiento, respuesta.getBody())
            );
        }

        @Test
        void recargar_cuandoLaClaveSeRepite_responde200() {
            // Arrange
            RecargaRequest request = new RecargaRequest(new BigDecimal("200.00"), FECHA);
            when(carteraService.recargar(any(), eq("clave"), eq(request))).thenReturn(Resultado.existente(movimiento));

            // Act & Assert
            assertEquals(HttpStatus.OK, controlador.recargar("clave", request, jwt).getStatusCode());
        }
    }

    @Nested
    class Movimientos {

        @Test
        void movimientos_delegaFiltrosYPaginacion() {
            // Arrange
            PaginaResponse<MovimientoResponse> pagina = new PaginaResponse<>(List.of(movimiento), 1, 10, 11, 2);
            when(carteraService.movimientos(any(), eq(TipoMovimiento.PAGO), eq(FECHA), eq(FECHA), eq(1), eq(10)))
                    .thenReturn(pagina);

            // Act & Assert
            assertSame(pagina, controlador.movimientos(TipoMovimiento.PAGO, FECHA, FECHA, 1, 10, jwt));
        }
    }

    @Nested
    class Pagar {

        @Test
        void pagar_cuandoEsNuevo_responde201ConLocation() {
            // Arrange
            PagoRequest request = new PagoRequest(TipoReferencia.BOLETOS, idReferencia, new BigDecimal("170.00"),
                    FECHA, null);
            when(pagoService.pagar(any(), eq(idReferencia.toString()), eq(request))).thenReturn(Resultado.nuevo(pago));

            // Act
            ResponseEntity<PagoResponse> respuesta = controlador.pagar(idReferencia.toString(), request, jwt);

            // Assert
            String ubicacion = respuesta.getHeaders().getLocation().toString();
            assertAll(
                    () -> assertEquals(HttpStatus.CREATED, respuesta.getStatusCode()),
                    () -> assertSame(pago, respuesta.getBody()),
                    () -> assertTrue(ubicacion.endsWith("/v1/customers/cartera/pagos?tipoReferencia=BOLETOS&idReferencia="
                            + idReferencia))
            );
        }

        @Test
        void pagar_cuandoYaExistia_responde200SinLocation() {
            // Arrange
            PagoRequest request = new PagoRequest(TipoReferencia.ALQUILER, idReferencia, BigDecimal.TEN, FECHA, null);
            when(pagoService.pagar(any(), eq(idReferencia.toString()), eq(request)))
                    .thenReturn(Resultado.existente(pago));

            // Act
            ResponseEntity<PagoResponse> respuesta = controlador.pagar(idReferencia.toString(), request, jwt);

            // Assert
            assertAll(
                    () -> assertEquals(HttpStatus.OK, respuesta.getStatusCode()),
                    () -> assertNull(respuesta.getHeaders().getLocation())
            );
        }
    }

    @Nested
    class ConsultarPago {

        @Test
        void consultarPago_conJwt_filtraPorElUsuarioDelToken() {
            // Arrange
            PagoConsultaResponse consulta = new PagoConsultaResponse(UUID.randomUUID(), BigDecimal.ONE, FECHA);
            when(pagoService.consultarPago(idUsuario, TipoReferencia.BOLETOS, idReferencia)).thenReturn(consulta);

            // Act
            PagoConsultaResponse respuesta = controlador.consultarPago(TipoReferencia.BOLETOS, idReferencia,
                    new JwtAuthenticationToken(jwt));

            // Assert
            assertSame(consulta, respuesta);
        }

        @Test
        void consultarPago_conClaveInterna_consultaSinUsuario() {
            // Arrange
            PagoConsultaResponse consulta = new PagoConsultaResponse(UUID.randomUUID(), BigDecimal.ONE, FECHA);
            when(pagoService.consultarPago(null, TipoReferencia.ALQUILER, idReferencia)).thenReturn(consulta);
            ArgumentCaptor<UUID> captor = ArgumentCaptor.forClass(UUID.class);

            // Act
            controlador.consultarPago(TipoReferencia.ALQUILER, idReferencia,
                    UsernamePasswordAuthenticationToken.authenticated("interno", null,
                            List.of(new SimpleGrantedAuthority(FiltroClaveInterna.ROL_INTERNO))));

            // Assert
            verify(pagoService).consultarPago(captor.capture(), eq(TipoReferencia.ALQUILER), eq(idReferencia));
            assertNull(captor.getValue());
        }
    }

    @Nested
    class UsuarioDelToken {

        @Test
        void usuarioAutenticado_leeLosClaimsDelJwt() {
            // Act
            UsuarioAutenticado usuario = new ConvertidorUsuarioAutenticado().desde(jwt);

            // Assert
            assertAll(
                    () -> assertEquals(idUsuario, usuario.id()),
                    () -> assertEquals(Rol.CLIENTE, usuario.rol()),
                    () -> assertNull(usuario.idSucursal())
            );
        }
    }
}
