package org.transportsgt.fleetservice.services.consulta;

import org.springframework.data.domain.Pageable;
import org.transportsgt.eventos.payload.flota.EstadoOperativo;
import org.transportsgt.fleetservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.fleetservice.dto.response.BusResponse;
import org.transportsgt.fleetservice.dto.response.ChoferResponse;
import org.transportsgt.fleetservice.dto.response.PaginaResponse;

import java.util.UUID;

public interface ConsultaFlotaService {

    PaginaResponse<BusResponse> listarBuses(UUID idSucursal, EstadoOperativo estadoOperativo, Boolean activo,
                                            String texto, Pageable pageable, UsuarioAutenticado autenticado);

    BusResponse obtenerBus(UUID id, UsuarioAutenticado autenticado);

    PaginaResponse<ChoferResponse> listarChoferes(UUID idSucursal, Boolean activo, String texto,
                                                  Pageable pageable, UsuarioAutenticado autenticado);

    ChoferResponse obtenerChofer(UUID id, UsuarioAutenticado autenticado);
}
