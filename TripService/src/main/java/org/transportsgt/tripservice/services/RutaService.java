package org.transportsgt.tripservice.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.tripservice.config.security.UsuarioAutenticado;
import org.transportsgt.tripservice.dto.request.Requests;
import org.transportsgt.tripservice.dto.response.Responses;
import org.transportsgt.tripservice.eventos.EventosViaje;
import org.transportsgt.tripservice.exception.OperacionNoAutorizadaException;
import org.transportsgt.tripservice.exception.ReglaNegocioException;
import org.transportsgt.tripservice.exception.RutaNoEncontradaException;
import org.transportsgt.tripservice.models.Ruta;
import org.transportsgt.tripservice.repositories.RutaRepository;
import org.transportsgt.tripservice.repositories.SucursalRefRepository;
import org.transportsgt.tripservice.repositories.ViajeRepository;

import java.util.List;
import java.util.UUID;

/**
 * Rutas de sucursal a sucursal. Las gestiona solo el administrador de la
 * sucursal de origen
 * y se eliminan solo si no tienen viajes asociados.
 */
@Service
@RequiredArgsConstructor
public class RutaService {

    private final RutaRepository rutaRepository;
    private final ViajeRepository viajeRepository;
    private final SucursalRefRepository sucursalRefRepository;
    private final EventosViaje eventos;

    @Transactional(readOnly = true)
    public List<Responses.RutaResponse> listar(UUID idSucursalOrigen, UUID idSucursalDestino,
            UsuarioAutenticado usuario) {
        UUID origen = idSucursalOrigen;
        if (usuario.esAdminSucursal()) {
            if (origen != null && !usuario.perteneceASucursal(origen)) {
                throw new OperacionNoAutorizadaException("Solo puede consultar las rutas de su sucursal");
            }
            origen = usuario.idSucursal();
        }
        List<Ruta> rutas;
        if (origen != null && idSucursalDestino != null) {
            final UUID origenFiltro = origen;
            rutas = rutaRepository.findAll().stream()
                    .filter(r -> r.getIdSucursalOrigen().equals(origenFiltro)
                            && r.getIdSucursalDestino().equals(idSucursalDestino))
                    .toList();
        } else if (origen != null) {
            rutas = rutaRepository.findAllByIdSucursalOrigen(origen);
        } else {
            rutas = rutaRepository.findAll();
        }
        return rutas.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Responses.RutaResponse detalle(UUID id, UsuarioAutenticado usuario) {
        Ruta r = rutaRepository.findById(id).orElseThrow(() -> new RutaNoEncontradaException(id));
        if (usuario.esAdminSucursal() && !usuario.perteneceASucursal(r.getIdSucursalOrigen())) {
            throw new OperacionNoAutorizadaException("La ruta no pertenece a su sucursal");
        }
        return toResponse(r);
    }

    @Transactional
    public Responses.RutaResponse crear(Requests.RutaRequest request, UsuarioAutenticado usuario) {
        if (!usuario.perteneceASucursal(request.idSucursalOrigen())) {
            throw new OperacionNoAutorizadaException(
                    "Solo el administrador de la sucursal de origen puede crear la ruta");
        }
        if (request.idSucursalOrigen().equals(request.idSucursalDestino())) {
            throw new ReglaNegocioException("RUTA_INVALIDA", "El origen y el destino deben ser distintos");
        }
        if (!sucursalRefRepository.existsByIdAndActivaTrue(request.idSucursalOrigen())
                || !sucursalRefRepository.existsByIdAndActivaTrue(request.idSucursalDestino())) {
            throw new ReglaNegocioException("SUCURSAL_NO_ENCONTRADA",
                    "Ambas sucursales deben existir y estar activas");
        }
        if (rutaRepository.existsByIdSucursalOrigenAndIdSucursalDestino(
                request.idSucursalOrigen(), request.idSucursalDestino())) {
            throw new ReglaNegocioException("RUTA_DUPLICADA",
                    "Ya existe una ruta entre esas dos sucursales");
        }
        Ruta r = Ruta.builder()
                .idSucursalOrigen(request.idSucursalOrigen())
                .idSucursalDestino(request.idSucursalDestino())
                .distanciaKm(request.distanciaKm())
                .precioBoleto(request.precioBoleto())
                .creadoPor(usuario.id())
                .build();
        r = rutaRepository.save(r);
        eventos.rutaCreada(r);
        return toResponse(r);
    }

    @Transactional
    public Responses.RutaResponse actualizar(UUID id, Requests.RutaRequest request, UsuarioAutenticado usuario) {
        Ruta r = rutaRepository.findById(id).orElseThrow(() -> new RutaNoEncontradaException(id));
        if (!usuario.perteneceASucursal(r.getIdSucursalOrigen())) {
            throw new OperacionNoAutorizadaException(
                    "Solo el administrador de la sucursal de origen puede actualizar la ruta");
        }
        if (!request.idSucursalOrigen().equals(r.getIdSucursalOrigen())) {
            throw new ReglaNegocioException("RUTA_INVALIDA", "No se puede cambiar la sucursal de origen de la ruta");
        }
        if (request.idSucursalOrigen().equals(request.idSucursalDestino())) {
            throw new ReglaNegocioException("RUTA_INVALIDA", "El origen y el destino deben ser distintos");
        }
        if (rutaRepository.existsByIdSucursalOrigenAndIdSucursalDestinoAndIdNot(
                request.idSucursalOrigen(), request.idSucursalDestino(), id)) {
            throw new ReglaNegocioException("RUTA_DUPLICADA",
                    "Ya existe otra ruta entre esas dos sucursales");
        }
        r.setIdSucursalDestino(request.idSucursalDestino());
        r.setDistanciaKm(request.distanciaKm());
        r.setPrecioBoleto(request.precioBoleto());
        r = rutaRepository.save(r);
        eventos.rutaActualizada(r);
        return toResponse(r);
    }

    @Transactional
    public void eliminar(UUID id, UsuarioAutenticado usuario) {
        Ruta r = rutaRepository.findById(id).orElseThrow(() -> new RutaNoEncontradaException(id));
        if (!usuario.perteneceASucursal(r.getIdSucursalOrigen())) {
            throw new OperacionNoAutorizadaException(
                    "Solo el administrador de la sucursal de origen puede eliminar la ruta");
        }
        if (viajeRepository.existsByRutaId(id)) {
            throw new ReglaNegocioException("RUTA_CON_VIAJES",
                    "La ruta no se puede eliminar porque tiene viajes asociados");
        }
        rutaRepository.delete(r);
        eventos.rutaEliminada(id);
    }

    private Responses.RutaResponse toResponse(Ruta r) {
        return new Responses.RutaResponse(
                r.getId(), r.getIdSucursalOrigen(), r.getIdSucursalDestino(),
                r.getDistanciaKm(), r.getPrecioBoleto(), r.getCreadoEn(), r.getActualizadoEn());
    }
}
