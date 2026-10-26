package org.transportsgt.privaterentalservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalAlquilerExceptionHandler {

    private static final String PG_UNICO = "23505";
    private static final String PG_LLAVE_FORANEA = "23503";
    private static final String PG_CHECK = "23514";
    private static final String PG_RAISE = "P0001";

    private final FabricaRespuestaError fabricaRespuestaError;

    @ExceptionHandler(AlquilerException.class)
    public ResponseEntity<RespuestaError> handleAlquiler(AlquilerException ex, HttpServletRequest request) {
        if (ex.getStatus().is5xxServerError()) {
            log.error("Error de dominio [{}]: {}", ex.getCodigo(), ex.getMessage(), ex);
        }
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
        return build(HttpStatus.CONFLICT, "El registro fue modificado por otro usuario, recárguelo",
                "CONFLICTO_CONCURRENCIA", request);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<RespuestaError> handleDataAccess(DataAccessException ex, HttpServletRequest request) {
        Throwable causa = ex;
        while (causa != null) {
            if (causa instanceof SQLException sql) {
                String state = sql.getSQLState();
                if (PG_UNICO.equals(state)) {
                    return build(HttpStatus.CONFLICT, "Ya existe un registro con esos datos únicos",
                            "REGISTRO_DUPLICADO", request);
                }
                if (PG_LLAVE_FORANEA.equals(state)) {
                    return build(HttpStatus.BAD_REQUEST, "Referencia inválida a un registro inexistente",
                            "REFERENCIA_INVALIDA", request);
                }
                if (PG_CHECK.equals(state) || PG_RAISE.equals(state)) {
                    return build(HttpStatus.CONFLICT,
                            "La operación viola una regla de integridad: " + sql.getMessage(),
                            "TRANSICION_INVALIDA", request);
                }
            }
            causa = causa.getCause();
        }
        log.error("Error de acceso a datos", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado. Intente de nuevo más tarde",
                "ERROR_INTERNO", request);
    }

    @ExceptionHandler(SQLException.class)
    public ResponseEntity<RespuestaError> handleSql(SQLException ex, HttpServletRequest request) {
        log.error("Error SQL", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado. Intente de nuevo más tarde",
                "ERROR_INTERNO", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RespuestaError> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Error inesperado", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado. Intente de nuevo más tarde",
                "ERROR_INTERNO", request);
    }

    private ResponseEntity<RespuestaError> build(HttpStatus status, String mensaje, String codigo,
                                                 HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(fabricaRespuestaError.crear(status, mensaje, codigo, request.getRequestURI()));
    }
}
