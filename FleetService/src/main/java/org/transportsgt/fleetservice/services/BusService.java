package org.transportsgt.fleetservice.services;

import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;
import org.transportsgt.fleetservice.config.security.UsuarioAutenticado;
import org.transportsgt.fleetservice.dto.request.ActualizarBusRequest;
import org.transportsgt.fleetservice.dto.request.CambiarActivoRequest;
import org.transportsgt.fleetservice.dto.request.CambiarEstadoOperativoRequest;
import org.transportsgt.fleetservice.dto.request.CrearBusRequest;
import org.transportsgt.fleetservice.dto.response.BusResponse;
import org.transportsgt.fleetservice.dto.response.PaginaResponse;
import org.transportsgt.eventos.payload.EstadoOperativo;

import java.util.UUID;

public interface BusService {

    /**
     * Crea el bus en la sucursal del administrador. Publica BusRegistrado.
     */
    BusResponse crear(CrearBusRequest request, UsuarioAutenticado autenticado);

    /**
     * Paginado. ADMIN_SUCURSAL solo ve los de su sucursal.
     */
    PaginaResponse<BusResponse> listar(UUID idSucursal, EstadoOperativo estadoOperativo, Boolean activo,
                                       String texto, Pageable pageable, UsuarioAutenticado autenticado);

    BusResponse obtener(UUID id, UsuarioAutenticado autenticado);

    /**
     * Actualiza los datos editables. Publica BusActualizado.
     */
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

    /**
     * Guarda la foto (disco o bucket privado) y deja su referencia interna en foto_url.
     */
    BusResponse guardarFoto(UUID id, MultipartFile archivo, UsuarioAutenticado autenticado);

    /**
     * Foto privada del bus, con la misma autorización por sucursal que al consultarlo. 404 si no tiene foto.
     */
    FotoService.FotoLeida leerFoto(UUID id, UsuarioAutenticado autenticado);
}
