package org.transportsgt.privaterentalservice.services.cotizacion.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.privaterentalservice.config.security.UsuarioAutenticado;
import org.transportsgt.privaterentalservice.dto.request.CotizacionRequest;
import org.transportsgt.privaterentalservice.dto.response.AlquilerResponse;
import org.transportsgt.privaterentalservice.eventos.EventosAlquiler;
import org.transportsgt.privaterentalservice.exception.SolicitudInvalidaException;
import org.transportsgt.privaterentalservice.mappers.AlquilerMapper;
import org.transportsgt.privaterentalservice.models.Alquiler;
import org.transportsgt.privaterentalservice.models.CotizacionAlquiler;
import org.transportsgt.privaterentalservice.models.EstadoAlquiler;
import org.transportsgt.privaterentalservice.models.TipoCotizacion;
import org.transportsgt.privaterentalservice.repositories.CotizacionAlquilerRepository;
import org.transportsgt.privaterentalservice.services.autorizacion.AutorizacionAlquiler;
import org.transportsgt.privaterentalservice.services.comun.BuscadorAlquileres;
import org.transportsgt.privaterentalservice.services.comun.GuardadoAlquiler;
import org.transportsgt.privaterentalservice.services.comun.TransicionesAlquiler;
import org.transportsgt.privaterentalservice.services.cotizacion.CotizacionAlquilerService;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CotizacionAlquilerServiceImpl implements CotizacionAlquilerService {

    private final BuscadorAlquileres buscador;
    private final AutorizacionAlquiler autorizacion;
    private final TransicionesAlquiler transiciones;
    private final GuardadoAlquiler guardado;
    private final CotizacionAlquilerRepository cotizacionRepository;
    private final EventosAlquiler eventos;
    private final AlquilerMapper mapper;

    @Override
    @Transactional
    public AlquilerResponse cotizar(UUID id, CotizacionRequest req, UsuarioAutenticado usuario) {
        Alquiler alquiler = buscador.porId(id);
        autorizacion.verificarGestion(alquiler, usuario);
        transiciones.verificar(alquiler, EstadoAlquiler.COTIZADO, "cotizar");
        validarLlegada(alquiler, req.fechaHoraLlegadaEstimada());

        alquiler.setPrecioFinal(req.precioFinal());
        alquiler.setFechaHoraLlegadaEstimada(req.fechaHoraLlegadaEstimada());
        transiciones.aplicar(alquiler, EstadoAlquiler.COTIZADO, "cotizar");
        alquiler = guardado.guardar(alquiler);
        cotizacionRepository.save(CotizacionAlquiler.builder()
                .alquiler(alquiler).tipo(TipoCotizacion.AJUSTADA_ADMIN).precio(req.precioFinal())
                .idUsuario(usuario.id()).comentario(req.comentario())
                .build());
        eventos.alquilerCotizado(alquiler, usuario.id());
        log.info("Alquiler {} cotizado alquiler {} por {}", alquiler.getId(), req.precioFinal(), usuario.id());
        return mapper.toResponse(alquiler);
    }

    /**
     * Valida que la llegada sea posterior alquiler la salida y que en ida y vuelta sea antes del retorno.
     *
     * @param alquiler
     * @param llegada
     */
    private void validarLlegada(Alquiler alquiler, OffsetDateTime llegada) {
        if (!llegada.isAfter(alquiler.getFechaHoraSalida())) {
            throw new SolicitudInvalidaException("La llegada estimada debe ser posterior alquiler la salida");
        }
        if (Boolean.TRUE.equals(alquiler.getIdaYVuelta()) && alquiler.getFechaHoraRetorno() != null
                && llegada.isBefore(alquiler.getFechaHoraRetorno())) {
            throw new SolicitudInvalidaException(
                    "En ida y vuelta la llegada estimada es el regreso: no puede ser antes del retorno");
        }
    }
}
