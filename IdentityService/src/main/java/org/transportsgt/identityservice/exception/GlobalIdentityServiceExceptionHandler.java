package org.transportsgt.identityservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.util.stream.Collectors;

/**
 * Traduce toda excepción a {@link RespuestaError}: { timestamp, status, error, codigo, ruta }.
 */
@Slf4j
@RestControllerAdvice
public class GlobalIdentityServiceExceptionHandler {

    static final String MENSAJE_CONCURRENCIA = "El registro fue modificado por otro usuario, recárguelo";
    static final String MENSAJE_INTERNO = "Ocurrió un error inesperado. Intente de nuevo más tarde";

    // SQLState de PostgreSQL
    private static final String PG_UNICO = "23505";
    private static final String PG_LLAVE_FORANEA = "23503";
    private static final String PG_CHECK = "23514";
    private static final String PG_INTEGRIDAD = "23000";   // fn_bloquear_eliminacion
    private static final String PG_RAISE = "P0001";        // RAISE EXCEPTION de los triggers

    @ExceptionHandler(IdentityServiceException.class)
    public ResponseEntity<RespuestaError> handleIdentityService(IdentityServiceException ex, HttpServletRequest request) {
        return build(ex.getStatus(), ex.getMessage(), ex.getCodigo(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RespuestaError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Error de validación");
        return build(HttpStatus.BAD_REQUEST, message, "VALIDACION_FALLIDA", request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<RespuestaError> handleMethodValidation(HandlerMethodValidationException ex,
                                                                 HttpServletRequest request) {
        String message = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(err -> r.getMethodParameter().getParameterName() + ": " + err.getDefaultMessage()))
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message.isEmpty() ? "Error de validación" : message,
                "VALIDACION_FALLIDA", request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<RespuestaError> handleConstraintViolation(ConstraintViolationException ex,
                                                                    HttpServletRequest request) {
        String message = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message, "VALIDACION_FALLIDA", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RespuestaError> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no es válido o tiene un formato incorrecto",
                "SOLICITUD_INVALIDA", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<RespuestaError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "El parámetro '" + ex.getName() + "' tiene un valor inválido",
                "SOLICITUD_INVALIDA", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<RespuestaError> handleMissingParameter(MissingServletRequestParameterException ex,
                                                                 HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Falta el parámetro '" + ex.getParameterName() + "'",
                "SOLICITUD_INVALIDA", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<RespuestaError> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "La ruta solicitada no existe", "RUTA_NO_ENCONTRADA", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<RespuestaError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                   HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "Método " + ex.getMethod() + " no permitido en esta ruta",
                "METODO_NO_PERMITIDO", request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<RespuestaError> handleOptimisticLock(ObjectOptimisticLockingFailureException ex,
                                                               HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, MENSAJE_CONCURRENCIA, "CONFLICTO_CONCURRENCIA", request);
    }

    /**
     * Restricciones y triggers de PostgreSQL. El servicio valida las mismas reglas antes de guardar,
     * así que llegar aquí es raro (p. ej. dos solicitudes simultáneas).
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<RespuestaError> handleDataAccess(DataAccessException ex, HttpServletRequest request) {
        SQLException sql = buscarSqlException(ex);
        String estado = sql == null ? null : sql.getSQLState();
        if (PG_UNICO.equals(estado)) {
            return build(HttpStatus.CONFLICT, mensajeUnico(sql.getMessage()), "REGISTRO_DUPLICADO", request);
        }
        if (PG_LLAVE_FORANEA.equals(estado)) {
            return build(HttpStatus.CONFLICT, "El registro hace referencia a datos que no existen",
                    "REFERENCIA_INVALIDA", request);
        }
        if (PG_CHECK.equals(estado)) {
            return build(HttpStatus.UNPROCESSABLE_CONTENT, mensajeCheck(sql.getMessage()), "DATOS_INCONSISTENTES", request);
        }
        if (PG_RAISE.equals(estado) || PG_INTEGRIDAD.equals(estado)) {
            return build(HttpStatus.UNPROCESSABLE_CONTENT, mensajeTrigger(sql.getMessage()), "REGLA_BASE_DATOS", request);
        }
        log.error("Error de acceso a datos en {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, MENSAJE_INTERNO, "ERROR_INTERNO", request);
    }

    /**
     * Incluye AuthorizationDeniedException (lanzada por @PreAuthorize).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<RespuestaError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "No tiene permisos para realizar esta operación", "ACCESO_DENEGADO", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<RespuestaError> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión para realizar esta operación", "NO_AUTENTICADO", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RespuestaError> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, MENSAJE_INTERNO, "ERROR_INTERNO", request);
    }

    private ResponseEntity<RespuestaError> build(HttpStatus status, String message, String codigo,
                                                 HttpServletRequest request) {
        return ResponseEntity.status(status).body(RespuestaError.de(status, message, codigo, request.getRequestURI()));
    }

    private static SQLException buscarSqlException(Throwable ex) {
        SQLException encontrada = null;
        for (Throwable t = ex; t != null && t.getCause() != t; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getSQLState() != null) {
                encontrada = sql;
            }
        }
        return encontrada;
    }

    private static String mensajeUnico(String detalle) {
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

    private static String mensajeCheck(String detalle) {
        String texto = detalle == null ? "" : detalle;
        if (texto.contains("ck_usuario_sucursal")) {
            return "Los usuarios ADMIN_SUCURSAL, CAJERO y CHOFER deben tener sucursal; ADMIN_SISTEMA y CLIENTE no";
        }
        return "Los datos no cumplen las reglas de validación";
    }

    /**
     * "ERROR: Debe existir al menos ...\n  Where: PL/pgSQL ..." → "Debe existir al menos ...".
     */
    static String mensajeTrigger(String detalle) {
        if (detalle == null || detalle.isBlank()) {
            return "La operación viola una regla de datos";
        }
        String linea = detalle.lines().findFirst().orElse(detalle).trim();
        return linea.startsWith("ERROR:") ? linea.substring("ERROR:".length()).trim() : linea;
    }
}
