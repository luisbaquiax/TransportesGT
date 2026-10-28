package org.transportsgt.identityservice.services.usuario.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.payload.identidad.PropositoContrasena;
import org.transportsgt.eventos.payload.identidad.Rol;
import org.transportsgt.identityservice.eventos.EventosIdentidad;
import org.transportsgt.identityservice.models.Sucursal;
import org.transportsgt.identityservice.models.Usuario;
import org.transportsgt.identityservice.repositories.UsuarioRepository;
import org.transportsgt.identityservice.services.contrasena.EnlacesContrasenaService;
import org.transportsgt.identityservice.services.usuario.AltaPersonalService;
import org.transportsgt.identityservice.utils.GeneradorTokenSeguro;
import org.transportsgt.identityservice.utils.NormalizadorCorreo;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RegistroPersonalServiceImpl implements AltaPersonalService {

    private final UsuarioRepository usuarioRepository;
    private final ValidadorUsuario validador;
    private final EnlacesContrasenaService enlaces;
    private final PasswordEncoder passwordEncoder;
    private final GeneradorTokenSeguro generadorToken;
    private final NormalizadorCorreo correos;
    private final EventosIdentidad eventos;

    @Override
    @Transactional
    public Usuario registrarPersonal(String nombreCompleto, String correo, Rol rol, Sucursal sucursal,
                                     UUID idCreador, String ip) {
        validador.rolDePersonal(rol);
        validador.sucursalSegunRol(rol, sucursal);
        String correoNormalizado = correos.normalizar(correo);
        validador.correoDisponible(correoNormalizado);
        validador.creador(idCreador, rol);

        Usuario usuario = Usuario.builder()
                .nombreCompleto(nombreCompleto.trim())
                .correo(correoNormalizado)
                // Valor aleatorio que nadie conoce: el usuario define su contraseña con el enlace de activación
                .hashContrasena(passwordEncoder.encode(generadorToken.generar()))
                .rol(rol)
                .sucursal(sucursal)
                .activo(true)
                .creadoPor(idCreador)
                .build();

        Usuario guardado = usuarioRepository.saveAndFlush(usuario);
        eventos.usuarioCreado(guardado);
        enlaces.emitir(guardado, PropositoContrasena.ACTIVACION, ip);
        return guardado;
    }
}
