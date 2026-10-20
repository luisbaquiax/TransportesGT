package org.transportsgt.ticketservice.services.consulta;

import org.transportsgt.ticketservice.dto.response.AsientoMaximoResponse;
import org.transportsgt.ticketservice.dto.response.MapaAsientosResponse;
import org.transportsgt.ticketservice.dto.response.ViajeDisponibleResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Consultas de solo lectura sobre los viajes a la venta y sus asientos.
 */
public interface ConsultaViajesService {

    /**
     * Viajes {@code PROGRAMADO}, no eliminados, con salida futura y bus asignado. Filtros opcionales.
     *
     * @param destino parte del nombre del destino (el catálogo no trae el id de la sucursal destino)
     * @param fecha   día de salida, en hora de Guatemala
     */
    List<ViajeDisponibleResponse> aLaVenta(UUID idOrigen, UUID idRuta, String destino, LocalDate fecha);

    MapaAsientosResponse mapaAsientos(UUID idViaje);

    AsientoMaximoResponse asientoMaximo(UUID idViaje);
}
