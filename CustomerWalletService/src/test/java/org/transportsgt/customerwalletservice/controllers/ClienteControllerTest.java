package org.transportsgt.customerwalletservice.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.transportsgt.customerwalletservice.config.security.UsuarioAutenticado;
import org.transportsgt.customerwalletservice.dto.request.ActualizarPerfilRequest;
import org.transportsgt.customerwalletservice.dto.response.ClienteBusquedaResponse;
import org.transportsgt.customerwalletservice.dto.response.ClienteResponse;
import org.transportsgt.customerwalletservice.fixture.ClienteFixture;
import org.transportsgt.customerwalletservice.services.ClienteService;
import org.transportsgt.eventos.payload.Rol;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClienteControllerTest {

    @Mock
    private ClienteService clienteService;

    private ClienteController controlador;
    private UUID idUsuario;
    private Jwt jwt;
    private ClienteResponse perfil;

    @BeforeEach
    void setUp() {
        controlador = new ClienteController(clienteService);
        idUsuario = UUID.randomUUID();
        jwt = ClienteFixture.jwt(idUsuario, Rol.CLIENTE);
        perfil = new ClienteResponse(idUsuario, ClienteFixture.NOMBRE, ClienteFixture.CORREO, ClienteFixture.NIT,
                null, null, null, true, new BigDecimal("10.00"));
    }

    @Nested
    class ObtenerPerfil {

        @Test
        void obtenerPerfil_usaElUsuarioDelToken() {
            // Arrange
            when(clienteService.obtenerPerfil(any())).thenReturn(perfil);
            ArgumentCaptor<UsuarioAutenticado> captor = ArgumentCaptor.forClass(UsuarioAutenticado.class);

            // Act
            ClienteResponse respuesta = controlador.obtenerPerfil(jwt);

            // Assert
            verify(clienteService).obtenerPerfil(captor.capture());
            assertEquals(idUsuario, captor.getValue().id());
            assertSame(perfil, respuesta);
        }
    }

    @Nested
    class ActualizarPerfil {

        @Test
        void actualizarPerfil_delegaElCuerpoYElUsuario() {
            // Arrange
            ActualizarPerfilRequest request = new ActualizarPerfilRequest("1234567-K", null, null, null);
            when(clienteService.actualizarPerfil(any(), eq(request))).thenReturn(perfil);

            // Act
            ClienteResponse respuesta = controlador.actualizarPerfil(request, jwt);

            // Assert
            assertSame(perfil, respuesta);
        }
    }

    @Nested
    class Buscar {

        @Test
        void buscar_delegaElNit() {
            // Arrange
            ClienteBusquedaResponse encontrado = new ClienteBusquedaResponse(idUsuario, ClienteFixture.NOMBRE,
                    ClienteFixture.NIT, true);
            when(clienteService.buscarPorNit("1234567-K")).thenReturn(encontrado);

            // Act & Assert
            assertSame(encontrado, controlador.buscar("1234567-K"));
        }
    }
}
