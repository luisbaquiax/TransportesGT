package org.transportsgt.tripservice.services.consulta;

import org.transportsgt.tripservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.tripservice.dto.request.Requests;
import org.transportsgt.tripservice.dto.response.PaginaResponse;
import org.transportsgt.tripservice.dto.response.Responses;

import java.util.List;
import java.util.UUID;

/** Consultas de viajes con sus reglas de visibilidad. */
public interface ConsultaViajesService {

    /** Máximo de ids por consulta de resumen: coincide con el tamaño máximo de página del historial de Boletos. */
    int MAX_IDS_RESUMEN = 100;

    /**
     * Página de viajes filtrada en la base de datos. SIS ve todos; SUC y CAJ los de su sucursal; CHO los suyos.
     * Ordenados por hora de salida (y por id, para que las páginas sean estables).
     */
    PaginaResponse<Responses.ViajeResponse> listar(Requests.FiltroViajes filtro, int pagina, int tamano,
                                                   UsuarioAutenticado usuario);

    /** Incluye los registros de salida y llegada. 403 si no le corresponde, 404 si no existe. */
    Responses.ViajeResponse detalle(UUID id, UsuarioAutenticado usuario);

    /**
     * Resumen público de varios viajes (historial de compras de cualquier usuario). Los ids que no existen, o cuyo
     * viaje se eliminó, se omiten. Sin restricción de sucursal: solo expone datos que ya son públicos al vender.
     */
    List<Responses.ViajeResumenResponse> resumen(List<UUID> ids);

    /** Viajes PROGRAMADO o EN_TRANSITO de un bus (interno: Flota, antes de desactivarlo). */
    List<Responses.ViajeResponse> activosPorBus(UUID idBus);
}
