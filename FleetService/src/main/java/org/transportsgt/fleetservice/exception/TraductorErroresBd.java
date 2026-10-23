package org.transportsgt.fleetservice.exception;

import org.springframework.stereotype.Component;

import java.sql.SQLException;

/**
 * Convierte los errores de PostgreSQL (únicos y triggers) en mensajes para el usuario.
 */
@Component
public class TraductorErroresBd {

    /**
     * La {@link SQLException} más interna con SQLState, o null.
     */
    public SQLException buscarSqlException(Throwable ex) {
        SQLException encontrada = null;
        for (Throwable t = ex; t != null && t.getCause() != t; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getSQLState() != null) {
                encontrada = sql;
            }
        }
        return encontrada;
    }

    public String mensajeUnico(String detalle) {
        String texto = detalle == null ? "" : detalle;
        if (texto.contains("uq_bus_placa")) {
            return "Ya existe un bus con esa placa";
        }
        if (texto.contains("chofer_numero_licencia_key")) {
            return "Ya existe un chofer con ese número de licencia";
        }
        if (texto.contains("chofer_id_usuario_key")) {
            return "Ese usuario ya está enlazado a otro chofer";
        }
        return "Ya existe un registro con esos datos";
    }

    /**
     * "ERROR: Los registros de ... \n  Where: PL/pgSQL ..." → "Los registros de ...".
     */
    public String mensajeTrigger(String detalle) {
        if (detalle == null || detalle.isBlank()) {
            return "La operación viola una regla de datos";
        }
        String linea = detalle.lines().findFirst().orElse(detalle).trim();
        return linea.startsWith("ERROR:") ? linea.substring("ERROR:".length()).trim() : linea;
    }
}
