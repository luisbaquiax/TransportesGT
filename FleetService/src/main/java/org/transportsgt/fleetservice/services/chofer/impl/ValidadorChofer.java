package org.transportsgt.fleetservice.services.chofer.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.fleetservice.exception.LicenciaDuplicadaException;
import org.transportsgt.fleetservice.exception.UsuarioYaEnlazadoException;
import org.transportsgt.fleetservice.repositories.ChoferRepository;

import java.util.UUID;

/**
 * Unicidad de la licencia y del usuario enlazado, validada antes de escribir (la base es la última defensa).
 */
@Component
@RequiredArgsConstructor
class ValidadorChofer {

    private final ChoferRepository choferRepository;

    void licenciaDisponible(String licencia) {
        if (choferRepository.existsByNumeroLicencia(licencia)) {
            throw new LicenciaDuplicadaException(licencia);
        }
    }

    void licenciaDisponibleParaOtro(String licencia, UUID idChofer) {
        if (choferRepository.existsByNumeroLicenciaAndIdNot(licencia, idChofer)) {
            throw new LicenciaDuplicadaException(licencia);
        }
    }

    void usuarioDisponible(UUID idUsuario) {
        if (idUsuario != null && choferRepository.existsByIdUsuario(idUsuario)) {
            throw new UsuarioYaEnlazadoException(idUsuario);
        }
    }

    void usuarioDisponibleParaOtro(UUID idUsuario, UUID idChofer) {
        if (idUsuario != null && choferRepository.existsByIdUsuarioAndIdNot(idUsuario, idChofer)) {
            throw new UsuarioYaEnlazadoException(idUsuario);
        }
    }
}
