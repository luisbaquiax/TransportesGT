package org.transportsgt.identityservice.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.payload.PropositoContrasena;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.identityservice.config.security.UsuarioAutenticado;
import org.transportsgt.identityservice.dto.request.ActualizarPerfilRequest;
import org.transportsgt.identityservice.dto.request.ActualizarUsuarioRequest;
import org.transportsgt.identityservice.dto.request.CrearUsuarioRequest;
import org.transportsgt.identityservice.dto.response.PaginaResponse;
import org.transportsgt.identityservice.dto.response.UsuarioResponse;
import org.transportsgt.identityservice.eventos.EventosIdentidad;
import org.transportsgt.identityservice.exception.ActivacionNoPendienteException;
import org.transportsgt.identityservice.exception.CorreoDuplicadoException;
import org.transportsgt.identityservice.exception.OperacionNoAutorizadaException;
import org.transportsgt.identityservice.exception.RegistroModificadoException;
import org.transportsgt.identityservice.exception.RolNoPermitidoException;
import org.transportsgt.identityservice.exception.SucursalInactivaException;
import org.transportsgt.identityservice.exception.SucursalNoEncontradaException;
import org.transportsgt.identityservice.exception.SucursalNoPermitidaException;
import org.transportsgt.identityservice.exception.UltimoAdministradorSistemaException;
import org.transportsgt.identityservice.exception.UltimoAdministradorSucursalException;
import org.transportsgt.identityservice.exception.UsuarioNoEncontradoException;
import org.transportsgt.identityservice.mappers.UsuarioMapper;
import org.transportsgt.identityservice.models.Sucursal;
import org.transportsgt.identityservice.models.Usuario;
import org.transportsgt.identityservice.repositories.SucursalRepository;
import org.transportsgt.identityservice.repositories.UsuarioRepository;
import org.transportsgt.identityservice.repositories.UsuarioSpecs;
import org.transportsgt.identityservice.services.TokenContrasenaService;
import org.transportsgt.identityservice.services.UsuarioService;
import org.transportsgt.identityservice.utils.Correos;
import org.transportsgt.identityservice.utils.GeneradorTokenSeguro;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UsuarioServiceImpl implements UsuarioService {

    /**
     * Roles que pertenecen a una sucursal (ck_usuario_sucursal).
     */
    private static final Set<Rol> ROLES_CON_SUCURSAL = EnumSet.of(Rol.ADMIN_SUCURSAL, Rol.CAJERO, Rol.CHOFER);
    private static final Set<Rol> CREA_ADMIN_SISTEMA = EnumSet.of(Rol.ADMIN_SISTEMA, Rol.ADMIN_SUCURSAL);
    private static final Set<Rol> CREA_ADMIN_SUCURSAL = EnumSet.of(Rol.CAJERO, Rol.CHOFER);

    private final UsuarioRepository usuarioRepository;
    private final SucursalRepository sucursalRepository;
    private final TokenContrasenaService tokenContrasenaService;
    private final PasswordEncoder passwordEncoder;
    private final GeneradorTokenSeguro generadorToken;
    private final UsuarioMapper usuarioMapper;
    private final EventosIdentidad eventos;

    @Override
    @Transactional
    public Usuario registrarPersonal(String nombreCompleto, String correo, Rol rol, Sucursal sucursal,
                                     UUID idCreador, String ip) {
        if (rol == Rol.CLIENTE) {
            throw new RolNoPermitidoException("Los clientes se registran por su cuenta en /auth/registro");
        }
        validarSucursalSegunRol(rol, sucursal);
        String correoNormalizado = Correos.normalizar(correo);
        if (usuarioRepository.existsByCorreoIgnoreCase(correoNormalizado)) {
            throw new CorreoDuplicadoException(correoNormalizado);
        }
        validarCreador(idCreador, rol);

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
        tokenContrasenaService.emitir(guardado, PropositoContrasena.ACTIVACION, ip);
        return guardado;
    }

    @Override
    @Transactional
    public UsuarioResponse crear(CrearUsuarioRequest request, UsuarioAutenticado autenticado, String ip) {
        Sucursal sucursal;
        if (autenticado.esAdminSistema() && CREA_ADMIN_SISTEMA.contains(request.rol())) {
            sucursal = request.idSucursal() == null ? null : buscarSucursal(request.idSucursal());
        } else if (autenticado.esAdminSucursal() && CREA_ADMIN_SUCURSAL.contains(request.rol())) {
            if (request.idSucursal() != null && !request.idSucursal().equals(autenticado.idSucursal())) {
                throw new OperacionNoAutorizadaException("Solo puede crear usuarios en su propia sucursal");
            }
            sucursal = buscarSucursal(autenticado.idSucursal());
        } else {
            throw new OperacionNoAutorizadaException(
                    "El rol " + autenticado.rol() + " no puede crear usuarios con rol " + request.rol());
        }
        Usuario creado = registrarPersonal(request.nombreCompleto(), request.correo(), request.rol(), sucursal,
                autenticado.id(), ip);
        return usuarioMapper.toResponse(creado);
    }

    @Override
    public PaginaResponse<UsuarioResponse> listar(Rol rol, UUID idSucursal, Boolean activo, String texto,
                                                  Pageable pageable, UsuarioAutenticado autenticado) {
        UUID filtroSucursal = idSucursal;
        if (autenticado.esAdminSucursal()) {
            if (idSucursal != null && !idSucursal.equals(autenticado.idSucursal())) {
                throw new OperacionNoAutorizadaException("Solo puede consultar los usuarios de su sucursal");
            }
            filtroSucursal = autenticado.idSucursal();
        } else if (!autenticado.esAdminSistema()) {
            throw new OperacionNoAutorizadaException("No puede consultar el listado de usuarios");
        }
        Page<Usuario> pagina = usuarioRepository.findAll(
                UsuarioSpecs.filtrar(rol, filtroSucursal, activo, texto), pageable);
        return PaginaResponse.de(pagina, usuarioMapper::toResponse);
    }

    @Override
    public UsuarioResponse obtener(UUID id, UsuarioAutenticado autenticado) {
        Usuario usuario = buscar(id);
        boolean mismoUsuario = usuario.getId().equals(autenticado.id());
        boolean mismaSucursal = autenticado.esAdminSucursal()
                && Objects.equals(idSucursalDe(usuario), autenticado.idSucursal());
        if (!mismoUsuario && !autenticado.esAdminSistema() && !mismaSucursal) {
            throw new OperacionNoAutorizadaException("No puede consultar este usuario");
        }
        return usuarioMapper.toResponse(usuario);
    }

    @Override
    @Transactional
    public UsuarioResponse actualizar(UUID id, ActualizarUsuarioRequest request, UsuarioAutenticado autenticado) {
        Usuario usuario = buscar(id);
        verificarPuedeGestionar(autenticado, usuario);
        if (request.version() != null && !request.version().equals(usuario.getVersion())) {
            throw new RegistroModificadoException();
        }
        String correo = Correos.normalizar(request.correo());
        if (!correo.equals(usuario.getCorreo()) && usuarioRepository.existsByCorreoIgnoreCaseAndIdNot(correo, id)) {
            throw new CorreoDuplicadoException(correo);
        }
        usuario.setNombreCompleto(request.nombreCompleto().trim());
        usuario.setCorreo(correo);

        Usuario guardado = usuarioRepository.saveAndFlush(usuario);
        eventos.usuarioActualizado(guardado);
        return usuarioMapper.toResponse(guardado);
    }

    @Override
    @Transactional
    public UsuarioResponse cambiarEstado(UUID id, boolean activo, UsuarioAutenticado autenticado) {
        Usuario usuario = buscar(id);
        verificarPuedeGestionar(autenticado, usuario);
        if (Boolean.valueOf(activo).equals(usuario.getActivo())) {
            return usuarioMapper.toResponse(usuario);   // sin cambio: no se publica evento
        }
        if (!activo) {
            validarNoEsUltimoAdministrador(usuario);
        }
        usuario.setActivo(activo);

        Usuario guardado = usuarioRepository.saveAndFlush(usuario);
        eventos.usuarioActualizado(guardado);
        return usuarioMapper.toResponse(guardado);
    }

    @Override
    @Transactional
    public void reenviarActivacion(UUID id, UsuarioAutenticado autenticado, String ip) {
        Usuario usuario = buscar(id);
        verificarPuedeGestionar(autenticado, usuario);
        if (usuario.getRol() == Rol.CLIENTE) {
            throw new RolNoPermitidoException("Los clientes definen su contraseña al registrarse");
        }
        if (!Boolean.TRUE.equals(usuario.getActivo()) || usuario.getContrasenaCambiadaEn() != null) {
            throw new ActivacionNoPendienteException();
        }
        tokenContrasenaService.emitir(usuario, PropositoContrasena.ACTIVACION, ip);
    }

    @Override
    @Transactional
    public UsuarioResponse actualizarPerfil(ActualizarPerfilRequest request, UsuarioAutenticado autenticado) {
        Usuario usuario = buscar(autenticado.id());
        usuario.setNombreCompleto(request.nombreCompleto().trim());

        Usuario guardado = usuarioRepository.saveAndFlush(usuario);
        eventos.usuarioActualizado(guardado);
        return usuarioMapper.toResponse(guardado);
    }

    @Override
    public Usuario buscar(UUID id) {
        return usuarioRepository.findById(id).orElseThrow(() -> new UsuarioNoEncontradoException(id));
    }

    /**
     * ADMIN_SISTEMA gestiona a cualquiera; ADMIN_SUCURSAL solo a CAJERO y CHOFER de su sucursal.
     */
    void verificarPuedeGestionar(UsuarioAutenticado autenticado, Usuario objetivo) {
        if (autenticado.esAdminSistema()) {
            return;
        }
        boolean permitido = autenticado.esAdminSucursal()
                && CREA_ADMIN_SUCURSAL.contains(objetivo.getRol())
                && Objects.equals(idSucursalDe(objetivo), autenticado.idSucursal());
        if (!permitido) {
            throw new OperacionNoAutorizadaException("No puede modificar este usuario");
        }
    }

    /**
     * Mismas reglas que los triggers fn_proteger_ultimo_admin y fn_proteger_ultimo_admin_sucursal
     * (que quedan como respaldo ante solicitudes simultáneas).
     */
    void validarNoEsUltimoAdministrador(Usuario usuario) {
        if (!Boolean.TRUE.equals(usuario.getActivo())) {
            return;
        }
        if (usuario.getRol() == Rol.ADMIN_SISTEMA && usuarioRepository.countByRolAndActivoTrue(Rol.ADMIN_SISTEMA) <= 1) {
            throw new UltimoAdministradorSistemaException();
        }
        if (usuario.getRol() == Rol.ADMIN_SUCURSAL && usuarioRepository
                .countBySucursalIdAndRolAndActivoTrue(idSucursalDe(usuario), Rol.ADMIN_SUCURSAL) <= 1) {
            throw new UltimoAdministradorSucursalException();
        }
    }

    /**
     * Igual que ck_usuario_sucursal: sucursal obligatoria para roles de sucursal, prohibida para los demás.
     */
    private void validarSucursalSegunRol(Rol rol, Sucursal sucursal) {
        if (ROLES_CON_SUCURSAL.contains(rol)) {
            if (sucursal == null) {
                throw new SucursalNoPermitidaException("El rol " + rol + " requiere una sucursal");
            }
            if (!Boolean.TRUE.equals(sucursal.getActiva())) {
                throw new SucursalInactivaException(sucursal.getId());
            }
        } else if (sucursal != null) {
            throw new SucursalNoPermitidaException("El rol " + rol + " no pertenece a una sucursal");
        }
    }

    /**
     * El creador debe seguir activo (su JWT puede ser anterior a la desactivación). Para ADMIN_SUCURSAL debe ser
     * ADMIN_SISTEMA, como exige el trigger trg_admin_sucursal_creador.
     */
    private void validarCreador(UUID idCreador, Rol rolNuevo) {
        Usuario creador = usuarioRepository.findById(idCreador)
                .filter(u -> Boolean.TRUE.equals(u.getActivo()))
                .orElseThrow(() -> new OperacionNoAutorizadaException("El usuario creador no está activo"));
        if (rolNuevo == Rol.ADMIN_SUCURSAL && creador.getRol() != Rol.ADMIN_SISTEMA) {
            throw new OperacionNoAutorizadaException(
                    "Solo un administrador de sistema puede crear administradores de sucursal");
        }
    }

    private Sucursal buscarSucursal(UUID id) {
        return sucursalRepository.findById(id).orElseThrow(() -> new SucursalNoEncontradaException(id));
    }

    private static UUID idSucursalDe(Usuario usuario) {
        return usuario.getSucursal() == null ? null : usuario.getSucursal().getId();
    }
}
