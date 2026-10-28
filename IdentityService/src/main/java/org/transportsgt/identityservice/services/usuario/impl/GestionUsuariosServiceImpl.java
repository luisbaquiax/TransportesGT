package org.transportsgt.identityservice.services.usuario.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.payload.identidad.PropositoContrasena;
import org.transportsgt.eventos.payload.identidad.Rol;
import org.transportsgt.identityservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.identityservice.dto.request.ActualizarPerfilRequest;
import org.transportsgt.identityservice.dto.request.ActualizarUsuarioRequest;
import org.transportsgt.identityservice.dto.request.CrearUsuarioRequest;
import org.transportsgt.identityservice.dto.response.UsuarioResponse;
import org.transportsgt.identityservice.eventos.EventosIdentidad;
import org.transportsgt.identityservice.exception.ActivacionNoPendienteException;
import org.transportsgt.identityservice.exception.RegistroModificadoException;
import org.transportsgt.identityservice.exception.RolNoPermitidoException;
import org.transportsgt.identityservice.mappers.UsuarioMapper;
import org.transportsgt.identityservice.models.Sucursal;
import org.transportsgt.identityservice.models.Usuario;
import org.transportsgt.identityservice.repositories.UsuarioRepository;
import org.transportsgt.identityservice.services.autorizacion.AutorizacionUsuarios;
import org.transportsgt.identityservice.services.comun.BuscadorIdentidad;
import org.transportsgt.identityservice.services.contrasena.EnlacesContrasenaService;
import org.transportsgt.identityservice.services.usuario.AltaPersonalService;
import org.transportsgt.identityservice.services.usuario.GestionUsuariosService;
import org.transportsgt.identityservice.utils.NormalizadorCorreo;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GestionUsuariosServiceImpl implements GestionUsuariosService {

    private final UsuarioRepository usuarioRepository;
    private final AltaPersonalService altaPersonal;
    private final EnlacesContrasenaService enlaces;
    private final AutorizacionUsuarios autorizacion;
    private final ValidadorUsuario validador;
    private final ReglasAdministradores reglasAdministradores;
    private final BuscadorIdentidad buscador;
    private final NormalizadorCorreo correos;
    private final UsuarioMapper usuarioMapper;
    private final EventosIdentidad eventos;

    @Override
    @Transactional
    public UsuarioResponse crear(CrearUsuarioRequest request, UsuarioAutenticado autenticado, String ip) {
        UUID idSucursal = autorizacion.sucursalDeAlta(autenticado, request.rol(), request.idSucursal());
        Sucursal sucursal = idSucursal == null ? null : buscador.sucursal(idSucursal);
        Usuario creado = altaPersonal.registrarPersonal(request.nombreCompleto(), request.correo(), request.rol(),
                sucursal, autenticado.id(), ip);
        return usuarioMapper.toResponse(creado);
    }

    @Override
    @Transactional
    public UsuarioResponse actualizar(UUID id, ActualizarUsuarioRequest request, UsuarioAutenticado autenticado) {
        Usuario usuario = cargarParaGestion(id, autenticado);
        if (request.version() != null && !request.version().equals(usuario.getVersion())) {
            throw new RegistroModificadoException();
        }
        String correo = correos.normalizar(request.correo());
        validador.correoDisponibleParaOtro(correo, usuario);
        usuario.setNombreCompleto(request.nombreCompleto().trim());
        usuario.setCorreo(correo);
        return publicarCambio(usuario);
    }

    @Override
    @Transactional
    public UsuarioResponse cambiarEstado(UUID id, boolean activo, UsuarioAutenticado autenticado) {
        Usuario usuario = cargarParaGestion(id, autenticado);
        if (Boolean.valueOf(activo).equals(usuario.getActivo())) {
            return usuarioMapper.toResponse(usuario);   // sin cambio: no se publica evento
        }
        if (!activo) {
            reglasAdministradores.verificarNoEsUltimo(usuario);
        }
        usuario.setActivo(activo);
        return publicarCambio(usuario);
    }

    @Override
    @Transactional
    public void reenviarActivacion(UUID id, UsuarioAutenticado autenticado, String ip) {
        Usuario usuario = cargarParaGestion(id, autenticado);
        if (usuario.getRol() == Rol.CLIENTE) {
            throw new RolNoPermitidoException("Los clientes definen su contraseña al registrarse");
        }
        if (!Boolean.TRUE.equals(usuario.getActivo()) || usuario.getContrasenaCambiadaEn() != null) {
            throw new ActivacionNoPendienteException();
        }
        enlaces.emitir(usuario, PropositoContrasena.ACTIVACION, ip);
    }

    @Override
    @Transactional
    public UsuarioResponse actualizarPerfil(ActualizarPerfilRequest request, UsuarioAutenticado autenticado) {
        Usuario usuario = buscador.usuario(autenticado.id());
        usuario.setNombreCompleto(request.nombreCompleto().trim());
        return publicarCambio(usuario);
    }

    private Usuario cargarParaGestion(UUID id, UsuarioAutenticado autenticado) {
        Usuario usuario = buscador.usuario(id);
        autorizacion.verificarGestion(autenticado, usuario);
        return usuario;
    }

    /** Guarda con flush para que UsuarioActualizado lleve la versión ya incrementada. */
    private UsuarioResponse publicarCambio(Usuario usuario) {
        Usuario guardado = usuarioRepository.saveAndFlush(usuario);
        eventos.usuarioActualizado(guardado);
        return usuarioMapper.toResponse(guardado);
    }
}
