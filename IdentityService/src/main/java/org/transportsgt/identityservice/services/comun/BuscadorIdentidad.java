package org.transportsgt.identityservice.services.comun;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.identityservice.exception.SucursalNoEncontradaException;
import org.transportsgt.identityservice.exception.UsuarioNoEncontradoException;
import org.transportsgt.identityservice.models.Sucursal;
import org.transportsgt.identityservice.models.Usuario;
import org.transportsgt.identityservice.repositories.SucursalRepository;
import org.transportsgt.identityservice.repositories.UsuarioRepository;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BuscadorIdentidad {

    private final UsuarioRepository usuarioRepository;
    private final SucursalRepository sucursalRepository;

    public Usuario usuario(UUID id) {
        return usuarioRepository.findById(id).orElseThrow(() -> new UsuarioNoEncontradoException(id));
    }

    public Sucursal sucursal(UUID id) {
        return sucursalRepository.findById(id).orElseThrow(() -> new SucursalNoEncontradaException(id));
    }
}
