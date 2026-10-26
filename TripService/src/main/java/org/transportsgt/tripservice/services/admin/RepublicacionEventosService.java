package org.transportsgt.tripservice.services.admin;

import org.transportsgt.tripservice.dto.response.Responses;

/**
 * Republica las instantáneas de rutas y viajes (RutaCreada, ViajeProgramado) para poblar las proyecciones de
 * Boletos, Alquileres y Reportes; los datos iniciales de la migración no generan eventos (plan §2.9).
 *
 * <p>Solo instantáneas: los hechos (SalidaRegistrada, LlegadaRegistrada) no se republican, porque sus
 * consumidores (Costos, Flota) los tratan como hechos nuevos. Es idempotente: cada evento lleva la versión
 * actual del agregado y los consumidores ignoran las que ya aplicaron.</p>
 */
public interface RepublicacionEventosService {

    Responses.RepublicacionResponse republicar();
}
