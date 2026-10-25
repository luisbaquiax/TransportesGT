package org.transportsgt.reportingservice.repositories.reportsadmin;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Ruta con coordenadas de origen y destino (proyección de la consulta nativa).
 */
public interface MapaRutaConsulta {
    UUID getIdRuta();
    String getOrigen();
    BigDecimal getLatitudOrigen();
    BigDecimal getLongitudOrigen();
    String getDestino();
    BigDecimal getLatitudDestino();
    BigDecimal getLongitudDestino();
    BigDecimal getDistanciaKm();
    BigDecimal getPrecioBoleto();
}
