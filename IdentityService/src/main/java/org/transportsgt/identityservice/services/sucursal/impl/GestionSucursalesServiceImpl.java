package org.transportsgt.identityservice.services.sucursal.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.payload.identidad.Rol;
import org.transportsgt.identityservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.identityservice.dto.request.ActualizarSucursalRequest;
import org.transportsgt.identityservice.dto.request.CrearSucursalRequest;
import org.transportsgt.identityservice.dto.request.NuevoAdministradorRequest;
import org.transportsgt.identityservice.dto.response.SucursalCreadaResponse;
import org.transportsgt.identityservice.dto.response.SucursalResponse;
import org.transportsgt.identityservice.eventos.EventosIdentidad;
import org.transportsgt.identityservice.exception.RegistroModificadoException;
import org.transportsgt.identityservice.mappers.SucursalMapper;
import org.transportsgt.identityservice.mappers.UsuarioMapper;
import org.transportsgt.identityservice.models.Sucursal;
import org.transportsgt.identityservice.models.Usuario;
import org.transportsgt.identityservice.repositories.SucursalRepository;
import org.transportsgt.identityservice.services.comun.BuscadorIdentidad;
import org.transportsgt.identityservice.services.sucursal.GestionSucursalesService;
import org.transportsgt.identityservice.services.usuario.AltaPersonalService;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GestionSucursalesServiceImpl implements GestionSucursalesService {

    private final SucursalRepository sucursalRepository;
    private final ValidadorSucursal validador;
    private final BuscadorIdentidad buscador;
    private final AltaPersonalService altaPersonal;
    private final SucursalMapper sucursalMapper;
    private final UsuarioMapper usuarioMapper;
    private final EventosIdentidad eventos;

    @Override
    @Transactional
    public SucursalCreadaResponse crear(CrearSucursalRequest request, UsuarioAutenticado autenticado, String ip) {
        String nombre = request.sucursal().nombre().trim();
        validador.nombreDisponible(nombre);
        Sucursal sucursal = sucursalMapper.toEntity(request.sucursal());
        sucursal.setNombre(nombre);
        sucursal.setActiva(true);

        Sucursal guardada = sucursalRepository.saveAndFlush(sucursal);
        eventos.sucursalCreada(guardada);

        NuevoAdministradorRequest datosAdmin = request.administrador();
        Usuario administrador = altaPersonal.registrarPersonal(datosAdmin.nombreCompleto(), datosAdmin.correo(),
                Rol.ADMIN_SUCURSAL, guardada, autenticado.id(), ip);

        return new SucursalCreadaResponse(sucursalMapper.toResponse(guardada), usuarioMapper.toResponse(administrador));
    }

    @Override
    @Transactional
    public SucursalResponse actualizar(UUID id, ActualizarSucursalRequest request) {
        Sucursal sucursal = buscador.sucursal(id);
        if (request.version() != null && !request.version().equals(sucursal.getVersion())) {
            throw new RegistroModificadoException();
        }
        String nombre = request.sucursal().nombre().trim();
        validador.nombreDisponibleParaOtra(nombre, id);
        sucursalMapper.actualizar(sucursal, request.sucursal());
        sucursal.setNombre(nombre);
        return publicarCambio(sucursal);
    }

    @Override
    @Transactional
    public SucursalResponse cambiarEstado(UUID id, boolean activa) {
        Sucursal sucursal = buscador.sucursal(id);
        if (Boolean.valueOf(activa).equals(sucursal.getActiva())) {
            return sucursalMapper.toResponse(sucursal);   // sin cambio: no se publica evento
        }
        sucursal.setActiva(activa);
        return publicarCambio(sucursal);
    }

    /** Guarda con flush para que SucursalActualizada lleve la versión ya incrementada. */
    private SucursalResponse publicarCambio(Sucursal sucursal) {
        Sucursal guardada = sucursalRepository.saveAndFlush(sucursal);
        eventos.sucursalActualizada(guardada);
        return sucursalMapper.toResponse(guardada);
    }
}
