package org.transportsgt.privaterentalservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.validation.ConstraintViolation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalAlquilerExceptionHandlerTest {

    private final GlobalAlquilerExceptionHandler handler = new GlobalAlquilerExceptionHandler();

    @Mock
    HttpServletRequest request;

    private void ruta() {
        when(request.getRequestURI()).thenReturn("/alquileres");
    }

    @Test
    void alquiler4xx() {
        ruta();
        ResponseEntity<RespuestaError> r = handler.handleAlquiler(
                new AlquilerNoEncontradoException(UUID.randomUUID()), request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(r.getBody().codigo()).isEqualTo("ALQUILER_NO_ENCONTRADO");
    }

    @Test
    void alquiler5xx() {
        ruta();
        ResponseEntity<RespuestaError> r = handler.handleAlquiler(
                new ServicioNoDisponibleException("Cartera caída"), request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(r.getBody().codigo()).isEqualTo("SERVICIO_NO_DISPONIBLE");
    }

    @Test
    void alquiler5xxConCausa() {
        ruta();
        ResponseEntity<RespuestaError> r = handler.handleAlquiler(
                new ServicioNoDisponibleException("Cartera", new RuntimeException("causa")), request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void validacionMetodo() {
        ruta();
        var br = new BeanPropertyBindingResult(new Object(), "solicitud");
        br.addError(new FieldError("solicitud", "nombreDestino", "requerido"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                new MethodParameter(GlobalAlquilerExceptionHandlerTest.class
                        .getDeclaredMethods()[0], -1), br);
        ResponseEntity<RespuestaError> r = handler.handleValidation(ex, request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody().codigo()).isEqualTo("VALIDACION_FALLIDA");
        assertThat(r.getBody().error()).contains("nombreDestino: requerido");
    }

    @Test
    void validacionMetodoVacia() {
        ruta();
        var br = new BeanPropertyBindingResult(new Object(), "solicitud");
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                new MethodParameter(GlobalAlquilerExceptionHandlerTest.class
                        .getDeclaredMethods()[0], -1), br);
        ResponseEntity<RespuestaError> r = handler.handleValidation(ex, request);
        assertThat(r.getBody().error()).isEqualTo("Error de validación");
    }

    @Test
    void validacionMetodoSpring() {
        ruta();
        MethodValidationResult resultado = org.mockito.Mockito.mock(MethodValidationResult.class);
        org.mockito.Mockito.when(resultado.getParameterValidationResults()).thenReturn(List.of());
        HandlerMethodValidationException ex = new HandlerMethodValidationException(resultado);
        ResponseEntity<RespuestaError> r = handler.handleMethodValidation(ex, request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody().error()).isEqualTo("Error de validación");
    }

    @Test
    void violacionConstraint() {
        ruta();
        Set<ConstraintViolation<Object>> violaciones = Set.of();
        ConstraintViolationException ex = new ConstraintViolationException("x", violaciones);
        ResponseEntity<RespuestaError> r = handler.handleConstraintViolation(ex, request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody().codigo()).isEqualTo("VALIDACION_FALLIDA");
    }

    @Test
    void cuerpoNoLegible() {
        ruta();
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "bad json", null, null);
        ResponseEntity<RespuestaError> r = handler.handleNotReadable(ex, request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody().codigo()).isEqualTo("SOLICITUD_INVALIDA");
    }

    @Test
    void tipoParametroIncorrecto() {
        ruta();
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "no-uuid", UUID.class, "idAlquiler", null, null);
        ResponseEntity<RespuestaError> r = handler.handleTypeMismatch(ex, request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody().error()).contains("idAlquiler");
    }

    @Test
    void parametroFaltante() {
        ruta();
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("page", "page");
        ResponseEntity<RespuestaError> r = handler.handleMissingParameter(ex, request);
        assertThat(r.getBody().error()).contains("page");
    }

    @Test
    void rutaInexistente() {
        ruta();
        ResponseEntity<RespuestaError> r = handler.handleNoResource(
                new org.springframework.web.servlet.resource.NoResourceFoundException(
                        null, "/x", null), request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(r.getBody().codigo()).isEqualTo("RUTA_NO_ENCONTRADA");
    }

    @Test
    void metodoNoPermitido() {
        ruta();
        ResponseEntity<RespuestaError> r = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("DELETE"), request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(r.getBody().codigo()).isEqualTo("METODO_NO_PERMITIDO");
    }

    @Test
    void optimisticoLock() {
        ruta();
        ResponseEntity<RespuestaError> r = handler.handleOptimisticLock(
                new ObjectOptimisticLockingFailureException("alquiler", null), request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody().codigo()).isEqualTo("CONFLICTO_CONCURRENCIA");
    }

    @Test
    void accesoDatosGenerico() {
        ruta();
        ResponseEntity<RespuestaError> r = handler.handleDataAccess(
                new DataIntegrityViolationException("db"), request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(r.getBody().codigo()).isEqualTo("ERROR_INTERNO");
    }

    @Test
    void accesoDatosSqlUnico() {
        ruta();
        SQLException sql = new SQLException("dup", "23505");
        ResponseEntity<RespuestaError> r = handler.handleDataAccess(
                new DataIntegrityViolationException("db", sql), request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody().codigo()).isEqualTo("REGISTRO_DUPLICADO");
    }

    @Test
    void accesoDatosSqlLlaveForanea() {
        ruta();
        SQLException sql = new SQLException("fk", "23503");
        ResponseEntity<RespuestaError> r = handler.handleDataAccess(
                new DataIntegrityViolationException("db", sql), request);
        assertThat(r.getBody().codigo()).isEqualTo("REFERENCIA_INVALIDA");
    }

    @Test
    void accesoDatosSqlCheck() {
        ruta();
        SQLException sql = new SQLException("chk", "23514");
        ResponseEntity<RespuestaError> r = handler.handleDataAccess(
                new DataIntegrityViolationException("db", sql), request);
        assertThat(r.getBody().codigo()).isEqualTo("TRANSICION_INVALIDA");
    }

    @Test
    void accesoDatosSqlRaise() {
        ruta();
        SQLException sql = new SQLException("raise", "P0001");
        ResponseEntity<RespuestaError> r = handler.handleDataAccess(
                new DataIntegrityViolationException("db", sql), request);
        assertThat(r.getBody().codigo()).isEqualTo("TRANSICION_INVALIDA");
    }

    @Test
    void sqlExcepcionDirecta() {
        ruta();
        ResponseEntity<RespuestaError> r = handler.handleSql(new SQLException("boom"), request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(r.getBody().codigo()).isEqualTo("ERROR_INTERNO");
    }

    @Test
    void excepcionGenerica() {
        ruta();
        ResponseEntity<RespuestaError> r = handler.handleGeneric(new RuntimeException("x"), request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(r.getBody().codigo()).isEqualTo("ERROR_INTERNO");
    }

    @Test
    void accesoDatosConCausaOptimistica() {
        ruta();
        SQLException sql = new SQLException("dup", "23505");
        var causa = new RuntimeException(new OptimisticLockingFailureException("x", sql));
        ResponseEntity<RespuestaError> r = handler.handleDataAccess(
                new DataIntegrityViolationException("db", causa), request);
        assertThat(r.getBody().codigo()).isEqualTo("REGISTRO_DUPLICADO");
    }

    @Test
    void solicitudInvalidaConCodigo() {
        ruta();
        ResponseEntity<RespuestaError> r = handler.handleAlquiler(
                new SolicitudInvalidaException("CODIGO_CUSTOM", "mensaje custom"), request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody().codigo()).isEqualTo("CODIGO_CUSTOM");
    }

    @Test
    void solicitudInvalidaDefault() {
        ruta();
        ResponseEntity<RespuestaError> r = handler.handleAlquiler(
                new SolicitudInvalidaException("falta destino"), request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody().codigo()).isEqualTo("SOLICITUD_INVALIDA");
    }
}
