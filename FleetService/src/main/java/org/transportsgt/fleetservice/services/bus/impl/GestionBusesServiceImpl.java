package org.transportsgt.fleetservice.services.bus.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.payload.flota.EstadoOperativo;
import org.transportsgt.fleetservice.clients.ConsultaViajes;
import org.transportsgt.fleetservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.fleetservice.dto.request.ActualizarBusRequest;
import org.transportsgt.fleetservice.dto.request.CambiarActivoRequest;
import org.transportsgt.fleetservice.dto.request.CambiarEstadoOperativoRequest;
import org.transportsgt.fleetservice.dto.request.CrearBusRequest;
import org.transportsgt.fleetservice.dto.response.BusResponse;
import org.transportsgt.fleetservice.eventos.EventosFlota;
import org.transportsgt.fleetservice.exception.BusConViajesException;
import org.transportsgt.fleetservice.mappers.BusMapper;
import org.transportsgt.fleetservice.models.Bus;
import org.transportsgt.fleetservice.repositories.BusRepository;
import org.transportsgt.fleetservice.services.autorizacion.AutorizacionSucursal;
import org.transportsgt.fleetservice.services.bus.GestionBusesService;
import org.transportsgt.fleetservice.services.comun.BuscadorFlota;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GestionBusesServiceImpl implements GestionBusesService {

    private final BusRepository busRepository;
    private final BuscadorFlota buscador;
    private final AutorizacionSucursal autorizacion;
    private final ValidadorBus validador;
    private final ConsultaViajes consultaViajes;
    private final BusMapper busMapper;
    private final EventosFlota eventos;

    @Override
    @Transactional
    public BusResponse crear(CrearBusRequest request, UsuarioAutenticado autenticado) {
        autorizacion.validarSucursalGestionada(autenticado.idSucursal());
        String placa = request.placa().trim();
        validador.placaDisponible(placa);
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
    @Transactional
    public BusResponse actualizar(UUID id, ActualizarBusRequest request, UsuarioAutenticado autenticado) {
        Bus bus = cargarParaGestion(id, request.version(), autenticado);
        String placa = request.placa().trim();
        validador.placaDisponibleParaOtro(placa, id);
        busMapper.actualizar(bus, request);
        bus.setPlaca(placa);
        return publicarCambio(bus);
    }

    @Override
    @Transactional
    public BusResponse cambiarActivo(UUID id, CambiarActivoRequest request, UsuarioAutenticado autenticado) {
        Bus bus = cargarParaGestion(id, request.version(), autenticado);
        boolean activo = request.activo();
        if (Boolean.valueOf(activo).equals(bus.getActivo())) {
            return busMapper.toResponse(bus);   // sin cambio: no se publica evento
        }
        // Falla cerrada: sin respuesta de Viajes no se asume que no hay viajes (plan §4.3)
        if (!activo && consultaViajes.hayViajesActivos(id)) {
            throw new BusConViajesException(id);
        }
        bus.setActivo(activo);
        return publicarCambio(bus);
    }

    @Override
    @Transactional
    public BusResponse cambiarEstadoOperativo(UUID id, CambiarEstadoOperativoRequest request,
                                              UsuarioAutenticado autenticado) {
        validador.estadoAsignable(request.estadoOperativo());
        Bus bus = cargarParaGestion(id, request.version(), autenticado);
        if (request.estadoOperativo() == bus.getEstadoOperativo()) {
            return busMapper.toResponse(bus);   // sin cambio: no se publica evento
        }
        bus.setEstadoOperativo(request.estadoOperativo());
        return publicarCambio(bus);
    }

    /** El bus, si el usuario lo gestiona y la versión enviada (si la hay) es la vigente. */
    private Bus cargarParaGestion(UUID id, Long version, UsuarioAutenticado autenticado) {
        Bus bus = buscador.bus(id);
        autorizacion.verificarGestion(autenticado, bus.getIdSucursal(), "bus");
        autorizacion.verificarVersion(version, bus.getVersion());
        return bus;
    }

    /** Guarda con flush para que BusActualizado lleve la versión ya incrementada. */
    private BusResponse publicarCambio(Bus bus) {
        Bus guardado = busRepository.saveAndFlush(bus);
        eventos.busActualizado(guardado);
        return busMapper.toResponse(guardado);
    }
}
