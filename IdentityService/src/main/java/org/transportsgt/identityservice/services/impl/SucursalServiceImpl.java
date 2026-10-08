package org.transportsgt.identityservice.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.identityservice.config.security.UsuarioAutenticado;
import org.transportsgt.identityservice.dto.request.ActualizarSucursalRequest;
import org.transportsgt.identityservice.dto.request.CrearSucursalRequest;
import org.transportsgt.identityservice.dto.request.NuevoAdministradorRequest;
import org.transportsgt.identityservice.dto.response.PaginaResponse;
import org.transportsgt.identityservice.dto.response.SucursalCreadaResponse;
import org.transportsgt.identityservice.dto.response.SucursalResponse;
import org.transportsgt.identityservice.dto.response.UsuarioResponse;
import org.transportsgt.identityservice.eventos.EventosIdentidad;
import org.transportsgt.identityservice.exception.NombreSucursalDuplicadoException;
import org.transportsgt.identityservice.exception.RegistroModificadoException;
import org.transportsgt.identityservice.exception.SucursalNoEncontradaException;
import org.transportsgt.identityservice.mappers.SucursalMapper;
import org.transportsgt.identityservice.mappers.UsuarioMapper;
import org.transportsgt.identityservice.models.Sucursal;
import org.transportsgt.identityservice.models.Usuario;
import org.transportsgt.identityservice.repositories.SucursalRepository;
import org.transportsgt.identityservice.repositories.SucursalSpecs;
import org.transportsgt.identityservice.repositories.UsuarioRepository;
import org.transportsgt.identityservice.services.SucursalService;
import org.transportsgt.identityservice.services.UsuarioService;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SucursalServiceImpl implements SucursalService {

    private final SucursalRepository sucursalRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;
    private final SucursalMapper sucursalMapper;
    private final UsuarioMapper usuarioMapper;
    private final EventosIdentidad eventos;

    @Override
    @Transactional
    public SucursalCreadaResponse crear(CrearSucursalRequest request, UsuarioAutenticado autenticado, String ip) {
        String nombre = request.sucursal().nombre().trim();
        if (sucursalRepository.existsByNombreIgnoreCase(nombre)) {
            throw new NombreSucursalDuplicadoException(nombre);
        }
        Sucursal sucursal = sucursalMapper.toEntity(request.sucursal());
        sucursal.setNombre(nombre);
        sucursal.setActiva(true);

        Sucursal guardada = sucursalRepository.saveAndFlush(sucursal);
        eventos.sucursalCreada(guardada);

        NuevoAdministradorRequest datosAdmin = request.administrador();
        Usuario administrador = usuarioService.registrarPersonal(datosAdmin.nombreCompleto(), datosAdmin.correo(),
                Rol.ADMIN_SUCURSAL, guardada, autenticado.id(), ip);

        return new SucursalCreadaResponse(sucursalMapper.toResponse(guardada), usuarioMapper.toResponse(administrador));
    }

    @Override
    public PaginaResponse<SucursalResponse> listar(Boolean activa, String nombre, Pageable pageable) {
        Page<Sucursal> pagina = sucursalRepository.findAll(SucursalSpecs.filtrar(activa, nombre), pageable);
        return PaginaResponse.de(pagina, sucursalMapper::toResponse);
    }

    @Override
    public SucursalResponse obtener(UUID id) {
        return sucursalMapper.toResponse(buscar(id));
    }

    @Override
    @Transactional
    public SucursalResponse actualizar(UUID id, ActualizarSucursalRequest request) {
        Sucursal sucursal = buscar(id);
        if (request.version() != null && !request.version().equals(sucursal.getVersion())) {
            throw new RegistroModificadoException();
        }
        String nombre = request.sucursal().nombre().trim();
        if (sucursalRepository.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw new NombreSucursalDuplicadoException(nombre);
        }
        sucursalMapper.actualizar(sucursal, request.sucursal());
        sucursal.setNombre(nombre);

        Sucursal guardada = sucursalRepository.saveAndFlush(sucursal);
        eventos.sucursalActualizada(guardada);
        return sucursalMapper.toResponse(guardada);
    }

    @Override
    @Transactional
    public SucursalResponse cambiarEstado(UUID id, boolean activa) {
        Sucursal sucursal = buscar(id);
        if (Boolean.valueOf(activa).equals(sucursal.getActiva())) {
            return sucursalMapper.toResponse(sucursal);   // sin cambio: no se publica evento
        }
        sucursal.setActiva(activa);
        Sucursal guardada = sucursalRepository.saveAndFlush(sucursal);
        eventos.sucursalActualizada(guardada);
        return sucursalMapper.toResponse(guardada);
    }

    @Override
    public List<UsuarioResponse> listarAdministradores(UUID id) {
        buscar(id);
        return usuarioRepository.findAllBySucursalIdAndRolOrderByNombreCompleto(id, Rol.ADMIN_SUCURSAL).stream()
                .map(usuarioMapper::toResponse)
                .toList();
    }

    @Override
    public Sucursal buscar(UUID id) {
        return sucursalRepository.findById(id).orElseThrow(() -> new SucursalNoEncontradaException(id));
    }
}
