package org.transportsgt.fleetservice.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.transportsgt.eventos.payload.EstadoOperativo;
import org.transportsgt.fleetservice.clients.ConsultaViajes;
import org.transportsgt.fleetservice.config.security.UsuarioAutenticado;
import org.transportsgt.fleetservice.dto.request.ActualizarBusRequest;
import org.transportsgt.fleetservice.dto.request.CambiarActivoRequest;
import org.transportsgt.fleetservice.dto.request.CambiarEstadoOperativoRequest;
import org.transportsgt.fleetservice.dto.request.CrearBusRequest;
import org.transportsgt.fleetservice.dto.response.BusResponse;
import org.transportsgt.fleetservice.dto.response.PaginaResponse;
import org.transportsgt.fleetservice.eventos.EventosFlota;
import org.transportsgt.fleetservice.exception.BusConViajesException;
import org.transportsgt.fleetservice.exception.BusNoEncontradoException;
import org.transportsgt.fleetservice.exception.EstadoOperativoInvalidoException;
import org.transportsgt.fleetservice.exception.OperacionNoAutorizadaException;
import org.transportsgt.fleetservice.exception.PlacaDuplicadaException;
import org.transportsgt.fleetservice.mappers.BusMapper;
import org.transportsgt.fleetservice.models.Bus;
import org.transportsgt.fleetservice.repositories.BusRepository;
import org.transportsgt.fleetservice.repositories.BusSpecs;
import org.transportsgt.fleetservice.services.AutorizacionSucursal;
import org.transportsgt.fleetservice.services.BusService;
import org.transportsgt.fleetservice.services.FotoService;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BusServiceImpl implements BusService {

    private final BusRepository busRepository;
    private final AutorizacionSucursal autorizacion;
    private final ConsultaViajes consultaViajes;
    private final FotoService fotoService;
    private final BusMapper busMapper;
    private final EventosFlota eventos;

    @Override
    @Transactional
    public BusResponse crear(CrearBusRequest request, UsuarioAutenticado autenticado) {
        autorizacion.validarSucursalGestionada(autenticado.idSucursal());
        String placa = request.placa().trim();
        if (busRepository.existsByPlacaIgnoreCase(placa)) {
            throw new PlacaDuplicadaException(placa);
        }
        Bus bus = Bus.builder()
                .idSucursal(autenticado.idSucursal())
                .placa(placa)
                .marca(request.marca().trim())
                .modelo(request.modelo().trim())
                .anioFabricacion(request.anioFabricacion())
                .capacidadPasajeros(request.capacidadPasajeros())
                .estadoOperativo(EstadoOperativo.DISPONIBLE)
                .kilometrajeActual(request.kilometrajeActual() == null
                        ? BigDecimal.ZERO : request.kilometrajeActual())
                .activo(true)
                .build();
        Bus guardado = busRepository.saveAndFlush(bus);
        eventos.busRegistrado(guardado);
        return busMapper.toResponse(guardado);
    }

    @Override
    public PaginaResponse<BusResponse> listar(UUID idSucursal, EstadoOperativo estadoOperativo, Boolean activo,
                                              String texto, Pageable pageable, UsuarioAutenticado autenticado) {
        UUID filtroSucursal = idSucursal;
        if (autenticado.esAdminSucursal()) {
            if (idSucursal != null && !idSucursal.equals(autenticado.idSucursal())) {
                throw new OperacionNoAutorizadaException("Solo puede consultar los buses de su sucursal");
            }
            filtroSucursal = autenticado.idSucursal();
        }
        Page<Bus> pagina = busRepository.findAll(
                BusSpecs.filtrar(filtroSucursal, estadoOperativo, activo, texto), pageable);
        return PaginaResponse.de(pagina, busMapper::toResponse);
    }

    @Override
    public BusResponse obtener(UUID id, UsuarioAutenticado autenticado) {
        Bus bus = buscar(id);
        autorizacion.verificarGestion(autenticado, bus.getIdSucursal(), "bus");
        return busMapper.toResponse(bus);
    }

    @Override
    @Transactional
    public BusResponse actualizar(UUID id, ActualizarBusRequest request, UsuarioAutenticado autenticado) {
        Bus bus = buscar(id);
        autorizacion.verificarGestion(autenticado, bus.getIdSucursal(), "bus");
        autorizacion.verificarVersion(request.version(), bus.getVersion());
        String placa = request.placa().trim();
        if (busRepository.existsByPlacaIgnoreCaseAndIdNot(placa, id)) {
            throw new PlacaDuplicadaException(placa);
        }
        busMapper.actualizar(bus, request);
        bus.setPlaca(placa);
        Bus guardado = busRepository.saveAndFlush(bus);
        eventos.busActualizado(guardado);
        return busMapper.toResponse(guardado);
    }

    @Override
    @Transactional
    public BusResponse cambiarActivo(UUID id, CambiarActivoRequest request, UsuarioAutenticado autenticado) {
        Bus bus = buscar(id);
        autorizacion.verificarGestion(autenticado, bus.getIdSucursal(), "bus");
        autorizacion.verificarVersion(request.version(), bus.getVersion());
        boolean activo = request.activo();
        if (Boolean.valueOf(activo).equals(bus.getActivo())) {
            return busMapper.toResponse(bus);   // sin cambio: no se publica evento
        }
        // Falla cerrada: sin respuesta de Viajes no se asume que no hay viajes (plan §4.3)
        if (!activo && consultaViajes.hayViajesActivos(id)) {
            throw new BusConViajesException(id);
        }
        bus.setActivo(activo);
        Bus guardado = busRepository.saveAndFlush(bus);
        eventos.busActualizado(guardado);
        return busMapper.toResponse(guardado);
    }

    @Override
    @Transactional
    public BusResponse cambiarEstadoOperativo(UUID id, CambiarEstadoOperativoRequest request,
                                              UsuarioAutenticado autenticado) {
        if (request.estadoOperativo() == EstadoOperativo.EN_TRANSITO) {
            throw new EstadoOperativoInvalidoException(
                    "EN_TRANSITO solo cambia al registrar la salida y la llegada de un viaje");
        }
        Bus bus = buscar(id);
        autorizacion.verificarGestion(autenticado, bus.getIdSucursal(), "bus");
        autorizacion.verificarVersion(request.version(), bus.getVersion());
        if (request.estadoOperativo() == bus.getEstadoOperativo()) {
            return busMapper.toResponse(bus);   // sin cambio: no se publica evento
        }
        bus.setEstadoOperativo(request.estadoOperativo());
        Bus guardado = busRepository.saveAndFlush(bus);
        eventos.busActualizado(guardado);
        return busMapper.toResponse(guardado);
    }

    @Override
    @Transactional
    public BusResponse guardarFoto(UUID id, MultipartFile archivo, UsuarioAutenticado autenticado) {
        Bus bus = buscar(id);
        autorizacion.verificarGestion(autenticado, bus.getIdSucursal(), "bus");
        bus.setFotoUrl(fotoService.guardar(FotoService.Tipo.BUSES, id, archivo));
        return busMapper.toResponse(busRepository.saveAndFlush(bus));
    }

    private Bus buscar(UUID id) {
        return busRepository.findById(id).orElseThrow(() -> new BusNoEncontradoException(id));
    }

}
