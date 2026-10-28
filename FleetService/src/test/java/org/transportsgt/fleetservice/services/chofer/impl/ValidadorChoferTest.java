package org.transportsgt.fleetservice.services.chofer.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.fleetservice.exception.UsuarioYaEnlazadoException;
import org.transportsgt.fleetservice.repositories.ChoferRepository;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidadorChoferTest {

    @Mock
    private ChoferRepository choferRepository;

    @InjectMocks
    private ValidadorChofer validador;

    private final UUID idUsuario = UUID.randomUUID();
    private final UUID idChofer = UUID.randomUUID();

    @Test
    void sinUsuarioNoConsultaElEnlace() {
        validador.usuarioDisponible(null);
        validador.usuarioDisponibleParaOtro(null, idChofer);
        verifyNoInteractions(choferRepository);
    }

    @Test
    void usuarioLibrePasa() {
        when(choferRepository.existsByIdUsuario(idUsuario)).thenReturn(false);
        when(choferRepository.existsByIdUsuarioAndIdNot(idUsuario, idChofer)).thenReturn(false);

        assertDoesNotThrow(() -> validador.usuarioDisponible(idUsuario));
        assertDoesNotThrow(() -> validador.usuarioDisponibleParaOtro(idUsuario, idChofer));
    }

    @Test
    void usuarioEnlazadoAOtroChoferSeRechaza() {
        when(choferRepository.existsByIdUsuario(idUsuario)).thenReturn(true);
        when(choferRepository.existsByIdUsuarioAndIdNot(idUsuario, idChofer)).thenReturn(true);

        assertThrows(UsuarioYaEnlazadoException.class, () -> validador.usuarioDisponible(idUsuario));
        assertThrows(UsuarioYaEnlazadoException.class,
                () -> validador.usuarioDisponibleParaOtro(idUsuario, idChofer));
    }
}
