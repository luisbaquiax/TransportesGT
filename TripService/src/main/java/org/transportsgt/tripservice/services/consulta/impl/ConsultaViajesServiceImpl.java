package org.transportsgt.tripservice.services.consulta.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.payload.EstadoViaje;
import org.transportsgt.tripservice.config.security.UsuarioAutenticado;
import org.transportsgt.tripservice.dto.request.Requests;
import org.transportsgt.tripservice.dto.response.PaginaResponse;
import org.transportsgt.tripservice.dto.response.Responses;
import org.transportsgt.tripservice.exception.SolicitudInvalidaException;
import org.transportsgt.tripservice.mappers.EnsambladorViajes;
import org.transportsgt.tripservice.mappers.FabricaPaginas;
import org.transportsgt.tripservice.models.Viaje;
import org.transportsgt.tripservice.repositories.ViajeRepository;
import org.transportsgt.tripservice.repositories.ViajeSpecs;
import org.transportsgt.tripservice.services.autorizacion.AutorizacionViaje;
import org.transportsgt.tripservice.services.comun.BuscadorViajes;
import org.transportsgt.tripservice.services.consulta.ConsultaViajesService;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConsultaViajesServiceImpl implements ConsultaViajesService {

    private static final EnumSet<EstadoViaje> ESTADOS_ACTIVOS =
            EnumSet.of(EstadoViaje.PROGRAMADO, EstadoViaje.EN_TRANSITO);
    /** Por salida y luego por id: con salidas iguales el orden no cambia entre páginas. */
    private static final Sort POR_SALIDA = Sort.by("fechaHoraSalida").and(Sort.by("id"));

    private final ViajeRepository viajeRepository;
    private final ViajeSpecs specs;
    private final FabricaPaginas fabricaPaginas;
    private final BuscadorViajes buscador;
    private final AutorizacionViaje autorizacion;
    private final EnsambladorViajes ensamblador;

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<Responses.ViajeResponse> listar(Requests.FiltroViajes filtro, int pagina, int tamano,
                                                          UsuarioAutenticado usuario) {
        Page<Viaje> viajes = viajeRepository.findAll(specs.filtrar(filtro, usuario),
                PageRequest.of(pagina, tamano, POR_SALIDA));
        return fabricaPaginas.de(viajes, ensamblador.respuestas(viajes.getContent()));
    }

    @Override
    @Transactional(readOnly = true)
    public Responses.ViajeResponse detalle(UUID id, UsuarioAutenticado usuario) {
        Viaje v = buscador.cargar(id);
        autorizacion.verificarLectura(v, usuario);
        return ensamblador.respuesta(v);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Responses.ViajeResumenResponse> resumen(List<UUID> ids) {
        Set<UUID> unicos = ids == null ? Set.of() : new LinkedHashSet<>(ids);
        unicos.remove(null);
        if (unicos.isEmpty()) {
            throw new SolicitudInvalidaException("Indique al menos un id de viaje");
        }
        if (unicos.size() > MAX_IDS_RESUMEN) {
            throw new SolicitudInvalidaException("Máximo " + MAX_IDS_RESUMEN + " ids por consulta");
        }
        return ensamblador.resumenes(viajeRepository.findAllConRutaByIdInOrderByFechaHoraSalida(unicos));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Responses.ViajeResponse> activosPorBus(UUID idBus) {
        return ensamblador.respuestas(
                viajeRepository.findAllByIdBusAndEstadoInOrderByFechaHoraSalida(idBus, ESTADOS_ACTIVOS));
    }
}
