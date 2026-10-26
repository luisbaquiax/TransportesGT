package org.transportsgt.fleetservice.services.consulta.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.payload.EstadoOperativo;
import org.transportsgt.fleetservice.config.security.UsuarioAutenticado;
import org.transportsgt.fleetservice.dto.response.BusResponse;
import org.transportsgt.fleetservice.dto.response.ChoferResponse;
import org.transportsgt.fleetservice.dto.response.PaginaResponse;
import org.transportsgt.fleetservice.exception.OperacionNoAutorizadaException;
import org.transportsgt.fleetservice.mappers.BusMapper;
import org.transportsgt.fleetservice.mappers.ChoferMapper;
import org.transportsgt.fleetservice.mappers.FabricaPaginas;
import org.transportsgt.fleetservice.models.Bus;
import org.transportsgt.fleetservice.models.Chofer;
import org.transportsgt.fleetservice.repositories.BusRepository;
import org.transportsgt.fleetservice.repositories.BusSpecs;
import org.transportsgt.fleetservice.repositories.ChoferRepository;
import org.transportsgt.fleetservice.repositories.ChoferSpecs;
import org.transportsgt.fleetservice.services.autorizacion.AutorizacionSucursal;
import org.transportsgt.fleetservice.services.comun.BuscadorFlota;
import org.transportsgt.fleetservice.services.consulta.ConsultaFlotaService;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultaFlotaServiceImpl implements ConsultaFlotaService {

    private final BusRepository busRepository;
    private final ChoferRepository choferRepository;
    private final BusSpecs busSpecs;
    private final ChoferSpecs choferSpecs;
    private final BuscadorFlota buscador;
    private final AutorizacionSucursal autorizacion;
    private final BusMapper busMapper;
    private final ChoferMapper choferMapper;
    private final FabricaPaginas paginas;

    @Override
    public PaginaResponse<BusResponse> listarBuses(UUID idSucursal, EstadoOperativo estadoOperativo, Boolean activo,
                                                   String texto, Pageable pageable, UsuarioAutenticado autenticado) {
        UUID sucursal = sucursalVisible(idSucursal, autenticado, "buses");
        return paginas.de(busRepository.findAll(busSpecs.filtrar(sucursal, estadoOperativo, activo, texto), pageable),
                busMapper::toResponse);
    }

    @Override
    public BusResponse obtenerBus(UUID id, UsuarioAutenticado autenticado) {
        Bus bus = buscador.bus(id);
        autorizacion.verificarGestion(autenticado, bus.getIdSucursal(), "bus");
        return busMapper.toResponse(bus);
    }

    @Override
    public PaginaResponse<ChoferResponse> listarChoferes(UUID idSucursal, Boolean activo, String texto,
                                                         Pageable pageable, UsuarioAutenticado autenticado) {
        UUID sucursal = sucursalVisible(idSucursal, autenticado, "choferes");
        return paginas.de(choferRepository.findAll(choferSpecs.filtrar(sucursal, activo, texto), pageable),
                choferMapper::toResponse);
    }

    @Override
    public ChoferResponse obtenerChofer(UUID id, UsuarioAutenticado autenticado) {
        Chofer chofer = buscador.chofer(id);
        autorizacion.verificarGestion(autenticado, chofer.getIdSucursal(), "chofer");
        return choferMapper.toResponse(chofer);
    }

    /**
     * SIS filtra por la sucursal que pida (o ninguna); ADMIN_SUCURSAL siempre por la suya.
     */
    private UUID sucursalVisible(UUID idSucursal, UsuarioAutenticado autenticado, String recurso) {
        if (!autenticado.esAdminSucursal()) {
            return idSucursal;
        }
        if (idSucursal != null && !idSucursal.equals(autenticado.idSucursal())) {
            throw new OperacionNoAutorizadaException("Solo puede consultar los " + recurso + " de su sucursal");
        }
        return autenticado.idSucursal();
    }
}
