package org.transportsgt.identityservice.services.sucursal.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.identityservice.exception.NombreSucursalDuplicadoException;
import org.transportsgt.identityservice.repositories.SucursalRepository;

import java.util.UUID;

/** Nombre único de la sucursal, validado antes de escribir (sucursal_nombre_key es la última defensa). */
@Component
@RequiredArgsConstructor
class ValidadorSucursal {

    private final SucursalRepository sucursalRepository;

    void nombreDisponible(String nombre) {
        if (sucursalRepository.existsByNombreIgnoreCase(nombre)) {
            throw new NombreSucursalDuplicadoException(nombre);
        }
    }

    void nombreDisponibleParaOtra(String nombre, UUID idSucursal) {
        if (sucursalRepository.existsByNombreIgnoreCaseAndIdNot(nombre, idSucursal)) {
            throw new NombreSucursalDuplicadoException(nombre);
        }
    }
}
