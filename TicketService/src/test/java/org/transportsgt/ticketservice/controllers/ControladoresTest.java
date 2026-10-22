package org.transportsgt.ticketservice.controllers;

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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.transportsgt.ticketservice.config.security.UsuarioAutenticado;
import org.transportsgt.ticketservice.dto.request.CompraRequest;
import org.transportsgt.ticketservice.dto.request.VentaMostradorRequest;
import org.transportsgt.ticketservice.dto.response.AsientoMaximoResponse;
import org.transportsgt.ticketservice.dto.response.CompraResponse;
import org.transportsgt.ticketservice.dto.response.MapaAsientosResponse;
import org.transportsgt.ticketservice.dto.response.PaginaResponse;
import org.transportsgt.ticketservice.dto.response.Resultado;
import org.transportsgt.ticketservice.dto.response.ResultadoCompra;
import org.transportsgt.ticketservice.dto.response.ViajeDisponibleResponse;
import org.transportsgt.ticketservice.fixture.ClienteRefFixture;
import org.transportsgt.ticketservice.fixture.CompraFixture;
import org.transportsgt.ticketservice.fixture.ViajeRefFixture;
import org.transportsgt.ticketservice.mappers.CompraMapperImpl;
import org.transportsgt.ticketservice.models.EstadoCompra;
import org.transportsgt.ticketservice.services.compra.CompraEnLineaService;
import org.transportsgt.ticketservice.services.consulta.ConsultaComprasService;
import org.transportsgt.ticketservice.services.consulta.ConsultaViajesService;
import org.transportsgt.ticketservice.services.compra.VentaMostradorService;
import org.transportsgt.eventos.payload.Rol;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ControladoresTest {

    private UUID idUsuario;
    private Jwt jwt;
    private CompraResponse compra;

    @BeforeEach
    void setUp() {
        idUsuario = UUID.randomUUID();
        jwt = ClienteRefFixture.jwt(idUsuario, Rol.CLIENTE, null);
        compra = new CompraMapperImpl().toResponse(CompraFixture.confirmada(UUID.randomUUID(),
                ViajeRefFixture.programado(UUID.randomUUID()), idUsuario, 12));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
                new MockHttpServletRequest("POST", "/v1/tickets/boletos/compras")));
    }

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Nested
    class Viajes {

        @Mock
        private ConsultaViajesService servicio;

        @Test
        void endpoints_delegan() {
            // Arrange
            ViajeController controlador = new ViajeController(servicio);
            UUID idViaje = UUID.randomUUID();
            LocalDate fecha = LocalDate.parse("2026-10-21");
            List<ViajeDisponibleResponse> viajes = List.of();
            MapaAsientosResponse mapa = new MapaAsientosResponse(idViaje, 40, List.of(1));
            AsientoMaximoResponse maximo = new AsientoMaximoResponse(idViaje, 1);
            when(servicio.aLaVenta(ViajeRefFixture.SUCURSAL_XELA, null, "capital", fecha)).thenReturn(viajes);
            when(servicio.mapaAsientos(idViaje)).thenReturn(mapa);
            when(servicio.asientoMaximo(idViaje)).thenReturn(maximo);

            // Act & Assert
            assertAll(
                    () -> assertSame(viajes, controlador.aLaVenta(ViajeRefFixture.SUCURSAL_XELA, null, "capital", fecha)),
                    () -> assertSame(mapa, controlador.mapaAsientos(idViaje)),
                    () -> assertSame(maximo, controlador.asientoMaximo(idViaje))
            );
        }
    }

    @Nested
    class Compras {

        @Mock
        private CompraEnLineaService compraEnLinea;
        @Mock
        private ConsultaComprasService consultaCompras;

        private CompraController controlador;
        private CompraRequest request;

        @BeforeEach
        void setUp() {
            controlador = new CompraController(compraEnLinea, consultaCompras);
            request = new CompraRequest(compra.idCompra(), compra.idViaje(), List.of(12), CompraFixture.FECHA_PAGO);
        }

        private ResponseEntity<CompraResponse> comprar(ResultadoCompra.Tipo tipo) {
            when(compraEnLinea.comprar(any(UsuarioAutenticado.class), eq("token-de-prueba"), eq(request)))
                    .thenReturn(new ResultadoCompra(compra, tipo));
            return controlador.comprar(request, jwt);
        }

        @Test
        void comprar_cuandoSeConfirma_responde201ConLocation() {
            // Act
            ResponseEntity<CompraResponse> respuesta = comprar(ResultadoCompra.Tipo.CONFIRMADA);

            // Assert
            assertAll(
                    () -> assertEquals(HttpStatus.CREATED, respuesta.getStatusCode()),
                    () -> assertEquals("http://localhost/boletos/compras/" + compra.idCompra(),
                            respuesta.getHeaders().getLocation().toString()),
                    () -> assertSame(compra, respuesta.getBody())
            );
        }

        @Test
        void comprar_cuandoYaExistia_responde200() {
            // Act
            ResponseEntity<CompraResponse> respuesta = comprar(ResultadoCompra.Tipo.EXISTENTE);

            // Assert
            assertAll(
                    () -> assertEquals(HttpStatus.OK, respuesta.getStatusCode()),
                    () -> assertNull(respuesta.getHeaders().getLocation())
            );
        }

        @Test
        void comprar_cuandoQuedaPendiente_responde202ConLocation() {
            // Act
            ResponseEntity<CompraResponse> respuesta = comprar(ResultadoCompra.Tipo.PENDIENTE);

            // Assert
            assertAll(
                    () -> assertEquals(HttpStatus.ACCEPTED, respuesta.getStatusCode()),
                    () -> assertEquals("http://localhost/boletos/compras/" + compra.idCompra(),
                            respuesta.getHeaders().getLocation().toString())
            );
        }

        @Test
        void consultas_usanElUsuarioDelToken() {
            // Arrange
            PaginaResponse<CompraResponse> pagina = new PaginaResponse<>(List.of(compra), 0, 20, 1, 1);
            ArgumentCaptor<UsuarioAutenticado> usuario = ArgumentCaptor.forClass(UsuarioAutenticado.class);
            when(consultaCompras.mias(usuario.capture(), isNull(), eq(0), eq(20))).thenReturn(pagina);
            when(consultaCompras.detalle(any(), eq(compra.idCompra()))).thenReturn(compra);
            when(consultaCompras.listado(any(), eq(compra.idViaje()), eq(EstadoCompra.CONFIRMADA), eq(0), eq(20)))
                    .thenReturn(pagina);

            // Act & Assert
            assertAll(
                    () -> assertSame(pagina, controlador.mias(null, 0, 20, jwt)),
                    () -> assertEquals(idUsuario, usuario.getValue().id()),
                    () -> assertSame(compra, controlador.detalle(compra.idCompra(), jwt)),
                    () -> assertSame(pagina, controlador.listado(compra.idViaje(), EstadoCompra.CONFIRMADA, 0, 20, jwt))
            );
        }
    }

    @Nested
    class VentasMostrador {

        @Mock
        private VentaMostradorService servicio;

        @Test
        void vender_cuandoEsNueva_responde201YCuandoSeRepite200() {
            // Arrange
            VentaMostradorController controlador = new VentaMostradorController(servicio);
            Jwt cajero = ClienteRefFixture.jwt(UUID.randomUUID(), Rol.CAJERO, ViajeRefFixture.SUCURSAL_XELA);
            VentaMostradorRequest request = new VentaMostradorRequest(compra.idCompra(), compra.idViaje(), List.of(12),
                    null, null, CompraFixture.FECHA_PAGO);
            when(servicio.vender(any(), eq(request))).thenReturn(Resultado.nuevo(compra), Resultado.existente(compra));

            // Act
            ResponseEntity<CompraResponse> nueva = controlador.vender(request, cajero);
            ResponseEntity<CompraResponse> repetida = controlador.vender(request, cajero);

            // Assert
            assertAll(
                    () -> assertEquals(HttpStatus.CREATED, nueva.getStatusCode()),
                    () -> assertEquals("http://localhost/boletos/compras/" + compra.idCompra(),
                            nueva.getHeaders().getLocation().toString()),
                    () -> assertEquals(HttpStatus.OK, repetida.getStatusCode())
            );
            verify(servicio, org.mockito.Mockito.times(2)).vender(any(), eq(request));
        }
    }
}
