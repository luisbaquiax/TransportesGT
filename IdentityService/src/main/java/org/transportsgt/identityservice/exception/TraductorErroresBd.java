package org.transportsgt.identityservice.exception;

import org.springframework.stereotype.Component;

import java.sql.SQLException;

/** Convierte los errores de PostgreSQL (únicos, checks y triggers) en mensajes para el usuario. */
@Component
public class TraductorErroresBd {

    /** La {@link SQLException} más interna con SQLState, o null. */
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
        if (texto.contains("uq_usuario_correo")) {
            return "Ya existe un usuario con ese correo";
        }
        if (texto.contains("sucursal_nombre_key")) {
            return "Ya existe una sucursal con ese nombre";
        }
        if (texto.contains("uq_token_activo")) {
            return "Ya hay un enlace vigente para este usuario; intente de nuevo";
        }
        return "Ya existe un registro con esos datos";
    }

    public String mensajeCheck(String detalle) {
        String texto = detalle == null ? "" : detalle;
        if (texto.contains("ck_usuario_sucursal")) {
            return "Los usuarios ADMIN_SUCURSAL, CAJERO y CHOFER deben tener sucursal; ADMIN_SISTEMA y CLIENTE no";
        }
        return "Los datos no cumplen las reglas de validación";
    }

    /** "ERROR: Debe existir al menos ...\n  Where: PL/pgSQL ..." → "Debe existir al menos ...". */
    public String mensajeTrigger(String detalle) {
        if (detalle == null || detalle.isBlank()) {
            return "La operación viola una regla de datos";
        }
        String linea = detalle.lines().findFirst().orElse(detalle).trim();
        return linea.startsWith("ERROR:") ? linea.substring("ERROR:".length()).trim() : linea;
    }
}
