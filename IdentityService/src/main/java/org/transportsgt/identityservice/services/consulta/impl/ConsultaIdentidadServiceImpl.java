package org.transportsgt.identityservice.services.consulta.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.identityservice.config.security.UsuarioAutenticado;
import org.transportsgt.identityservice.dto.response.PaginaResponse;
import org.transportsgt.identityservice.dto.response.SucursalResponse;
import org.transportsgt.identityservice.dto.response.UsuarioResponse;
import org.transportsgt.identityservice.mappers.FabricaPaginas;
import org.transportsgt.identityservice.mappers.SucursalMapper;
import org.transportsgt.identityservice.mappers.UsuarioMapper;
import org.transportsgt.identityservice.models.Usuario;
import org.transportsgt.identityservice.repositories.SucursalRepository;
import org.transportsgt.identityservice.repositories.SucursalSpecs;
import org.transportsgt.identityservice.repositories.UsuarioRepository;
import org.transportsgt.identityservice.repositories.UsuarioSpecs;
import org.transportsgt.identityservice.services.autorizacion.AutorizacionUsuarios;
import org.transportsgt.identityservice.services.comun.BuscadorIdentidad;
import org.transportsgt.identityservice.services.consulta.ConsultaIdentidadService;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultaIdentidadServiceImpl implements ConsultaIdentidadService {

    private final UsuarioRepository usuarioRepository;
    private final SucursalRepository sucursalRepository;
    private final UsuarioSpecs usuarioSpecs;
    private final SucursalSpecs sucursalSpecs;
    private final BuscadorIdentidad buscador;
    private final AutorizacionUsuarios autorizacion;
    private final UsuarioMapper usuarioMapper;
    private final SucursalMapper sucursalMapper;
    private final FabricaPaginas paginas;

    @Override
    public PaginaResponse<UsuarioResponse> listarUsuarios(Rol rol, UUID idSucursal, Boolean activo, String texto,
                                                          Pageable pageable, UsuarioAutenticado autenticado) {
        UUID sucursal = autorizacion.sucursalVisible(autenticado, idSucursal);
        return paginas.de(usuarioRepository.findAll(usuarioSpecs.filtrar(rol, sucursal, activo, texto), pageable),
                usuarioMapper::toResponse);
    }

    @Override
    public UsuarioResponse obtenerUsuario(UUID id, UsuarioAutenticado autenticado) {
        Usuario usuario = buscador.usuario(id);
        autorizacion.verificarLectura(autenticado, usuario);
        return usuarioMapper.toResponse(usuario);
    }

    @Override
    public PaginaResponse<SucursalResponse> listarSucursales(Boolean activa, String nombre, Pageable pageable) {
        return paginas.de(sucursalRepository.findAll(sucursalSpecs.filtrar(activa, nombre), pageable),
                sucursalMapper::toResponse);
    }

    @Override
    public SucursalResponse obtenerSucursal(UUID id) {
        return sucursalMapper.toResponse(buscador.sucursal(id));
    }

    @Override
    public List<UsuarioResponse> listarAdministradores(UUID idSucursal) {
        buscador.sucursal(idSucursal);
        return usuarioRepository.findAllBySucursalIdAndRolOrderByNombreCompleto(idSucursal, Rol.ADMIN_SUCURSAL).stream()
                .map(usuarioMapper::toResponse)
                .toList();
    }
}
