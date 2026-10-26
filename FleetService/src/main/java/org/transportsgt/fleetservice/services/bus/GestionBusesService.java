package org.transportsgt.fleetservice.services.bus;

import org.transportsgt.fleetservice.config.security.UsuarioAutenticado;
import org.transportsgt.fleetservice.dto.request.ActualizarBusRequest;
import org.transportsgt.fleetservice.dto.request.CambiarActivoRequest;
import org.transportsgt.fleetservice.dto.request.CambiarEstadoOperativoRequest;
import org.transportsgt.fleetservice.dto.request.CrearBusRequest;
import org.transportsgt.fleetservice.dto.response.BusResponse;

import java.util.UUID;

/** Cambios sobre los buses (solo ADMIN_SUCURSAL de la sucursal del bus). Cada cambio publica un evento. */
public interface GestionBusesService {

    /** Crea el bus en la sucursal del administrador. Publica BusRegistrado. */
    BusResponse crear(CrearBusRequest request, UsuarioAutenticado autenticado);

    /** Actualiza los datos editables. Publica BusActualizado. */
    BusResponse actualizar(UUID id, ActualizarBusRequest request, UsuarioAutenticado autenticado);

    /**
     * Para desactivar consulta a Viajes (GET /viajes/activos): si hay viajes programados o en
     * tránsito → 409 BUS_CON_VIAJES; si Viajes no responde → 503. Publica BusActualizado si cambia.
     */
    BusResponse cambiarActivo(UUID id, CambiarActivoRequest request, UsuarioAutenticado autenticado);

    /**
     * DISPONIBLE, EN_TALLER o FUERA_DE_SERVICIO (EN_TRANSITO solo con salida/llegada).
     * Publica BusActualizado si cambia.
     */
    BusResponse cambiarEstadoOperativo(UUID id, CambiarEstadoOperativoRequest request, UsuarioAutenticado autenticado);
}
