package org.transportsgt.identityservice.services.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.transportsgt.identityservice.dto.response.RepublicacionResponse;
import org.transportsgt.identityservice.eventos.EventosIdentidad;
import org.transportsgt.identityservice.fixtures.SucursalFixture;
import org.transportsgt.identityservice.fixtures.UsuarioFixture;
import org.transportsgt.identityservice.models.Sucursal;
import org.transportsgt.identityservice.models.Usuario;
import org.transportsgt.identityservice.repositories.SucursalRepository;
import org.transportsgt.identityservice.repositories.UsuarioRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepublicacionEventosServiceImplTest {

    @Mock
    private SucursalRepository sucursalRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private EventosIdentidad eventos;
    @InjectMocks
    private RepublicacionEventosServiceImpl republicacionService;

    @Test
    void republicar_publicaPrimeroSucursalesYLuegoUsuarios() {
        // Arrange
        Sucursal sucursal = SucursalFixture.activa(UUID.randomUUID());
        Usuario admin = UsuarioFixture.adminSistema(UUID.randomUUID());
        Usuario cajero = UsuarioFixture.cajero(UUID.randomUUID(), sucursal);
        when(sucursalRepository.findAll(any(Sort.class))).thenReturn(List.of(sucursal));
        when(usuarioRepository.findAllByOrderByCreadoEn()).thenReturn(List.of(admin, cajero));

        // Act
        RepublicacionResponse result = republicacionService.republicar();

        // Assert
        InOrder orden = inOrder(eventos);
        assertAll(
                () -> orden.verify(eventos).sucursalCreada(sucursal),
                () -> orden.verify(eventos).usuarioCreado(admin),
                () -> orden.verify(eventos).usuarioCreado(cajero),
                () -> assertEquals(1, result.sucursales()),
                () -> assertEquals(2, result.usuarios())
        );
    }

    @Test
    void republicar_sinDatos_noPublicaNada() {
        // Arrange
        when(sucursalRepository.findAll(any(Sort.class))).thenReturn(List.of());
        when(usuarioRepository.findAllByOrderByCreadoEn()).thenReturn(List.of());

        // Act
        RepublicacionResponse result = republicacionService.republicar();

        // Assert
        assertAll(
                () -> assertEquals(new RepublicacionResponse(0, 0), result),
                () -> verifyNoInteractions(eventos)
        );
    }
}
