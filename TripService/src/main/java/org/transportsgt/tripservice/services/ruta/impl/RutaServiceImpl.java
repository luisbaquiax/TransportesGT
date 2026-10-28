package org.transportsgt.tripservice.services.ruta.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.tripservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.tripservice.dto.request.Requests;
import org.transportsgt.tripservice.dto.response.Responses;
import org.transportsgt.tripservice.eventos.EventosViaje;
import org.transportsgt.tripservice.exception.ReglaNegocioException;
import org.transportsgt.tripservice.exception.RutaNoEncontradaException;
import org.transportsgt.tripservice.mappers.RutaMapper;
import org.transportsgt.tripservice.models.Ruta;
import org.transportsgt.tripservice.repositories.RutaRepository;
import org.transportsgt.tripservice.repositories.ViajeRepository;
import org.transportsgt.tripservice.services.ruta.RutaService;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RutaServiceImpl implements RutaService {

    private final RutaRepository rutaRepository;
    private final ViajeRepository viajeRepository;
    private final ValidadorRuta validador;
    private final RutaMapper rutaMapper;
    private final EventosViaje eventos;

    @Override
    @Transactional(readOnly = true)
    public List<Responses.RutaResponse> listar(UUID idSucursalOrigen, UUID idSucursalDestino) {
        List<Ruta> rutas;
        if (idSucursalOrigen != null && idSucursalDestino != null) {
            rutas = rutaRepository.findAllByIdSucursalOrigenAndIdSucursalDestino(idSucursalOrigen, idSucursalDestino);
        } else if (idSucursalOrigen != null) {
            rutas = rutaRepository.findAllByIdSucursalOrigen(idSucursalOrigen);
        } else if (idSucursalDestino != null) {
            rutas = rutaRepository.findAllByIdSucursalDestino(idSucursalDestino);
        } else {
            rutas = rutaRepository.findAll();
        }
        return rutas.stream().map(rutaMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Responses.RutaResponse detalle(UUID id) {
        return rutaMapper.toResponse(cargar(id));
    }

    @Override
    @Transactional
    public Responses.RutaResponse crear(Requests.RutaRequest request, UsuarioAutenticado usuario) {
        validador.verificarGestion(usuario, request.idSucursalOrigen(), "crear la ruta");
        validador.validarNueva(request);
        Ruta r = Ruta.builder()
                .idSucursalOrigen(request.idSucursalOrigen())
                .idSucursalDestino(request.idSucursalDestino())
                .distanciaKm(request.distanciaKm())
                .precioBoleto(request.precioBoleto())
                .creadoPor(usuario.id())
                .build();
        r = rutaRepository.saveAndFlush(r);
        eventos.rutaCreada(r);
        return rutaMapper.toResponse(r);
    }

    @Override
    @Transactional
    public Responses.RutaResponse actualizar(UUID id, Requests.RutaRequest request, UsuarioAutenticado usuario) {
        Ruta r = cargar(id);
        validador.verificarGestion(usuario, r.getIdSucursalOrigen(), "actualizar la ruta");
        validador.validarCambio(r, request);
        r.setIdSucursalDestino(request.idSucursalDestino());
        r.setDistanciaKm(request.distanciaKm());
        r.setPrecioBoleto(request.precioBoleto());
        // flush: RutaActualizada debe llevar la versión ya incrementada
        r = rutaRepository.saveAndFlush(r);
        eventos.rutaActualizada(r);
        return rutaMapper.toResponse(r);
    }

    @Override
    @Transactional
    public void eliminar(UUID id, UsuarioAutenticado usuario) {
        Ruta r = cargar(id);
        validador.verificarGestion(usuario, r.getIdSucursalOrigen(), "eliminar la ruta");
        if (viajeRepository.existsByRutaId(id)) {
            throw new ReglaNegocioException("RUTA_CON_VIAJES",
                    "La ruta no se puede eliminar porque tiene viajes asociados");
        }
        rutaRepository.delete(r);
        eventos.rutaEliminada(id);
    }

    private Ruta cargar(UUID id) {
        return rutaRepository.findById(id).orElseThrow(() -> new RutaNoEncontradaException(id));
    }
}
