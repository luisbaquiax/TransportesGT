package org.transportsgt.operationalcostsservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
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
class GlobalCostosExceptionHandlerTest {

    private final GlobalCostosExceptionHandler handler = new GlobalCostosExceptionHandler();

    @Mock
    HttpServletRequest request;

    private void ruta() {
        when(request.getRequestURI()).thenReturn("/costos");
    }

    @Test
    void costos4xx() {
        ruta();
        ResponseEntity<RespuestaError> r = handler.handleCostos(
                new NoEncontradoException("BUS_NO_ENCONTRADO", "No existe el bus"), request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(r.getBody().codigo()).isEqualTo("BUS_NO_ENCONTRADO");
    }

    @Test
    void costosConflict() {
        ruta();
        ResponseEntity<RespuestaError> r = handler.handleCostos(
                new ReglaNegocioException("CATEGORIA_CON_GASTOS", "tiene gastos"), request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody().codigo()).isEqualTo("CATEGORIA_CON_GASTOS");
    }

    @Test
    void costos403() {
        ruta();
        ResponseEntity<RespuestaError> r = handler.handleCostos(
                new OperacionNoAutorizadaException("otra sucursal"), request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(r.getBody().codigo()).isEqualTo("OPERACION_NO_AUTORIZADA");
    }

    @Test
    void costos400() {
        ruta();
        ResponseEntity<RespuestaError> r = handler.handleCostos(
                new ValidacionException("BUS_INACTIVO", "bus inactivo"), request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody().codigo()).isEqualTo("BUS_INACTIVO");
    }

    @Test
    void validacionMetodo() {
        ruta();
        var br = new BeanPropertyBindingResult(new Object(), "x");
        br.addError(new FieldError("x", "nombre", "requerido"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                new MethodParameter(GlobalCostosExceptionHandlerTest.class.getDeclaredMethods()[0], -1), br);
        ResponseEntity<RespuestaError> r = handler.handleValidation(ex, request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody().codigo()).isEqualTo("VALIDACION_FALLIDA");
    }

    @Test
    void validacionMetodoVacia() {
        ruta();
        var br = new BeanPropertyBindingResult(new Object(), "x");
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                new MethodParameter(GlobalCostosExceptionHandlerTest.class.getDeclaredMethods()[0], -1), br);
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
    }

    @Test
    void violacionConstraint() {
        ruta();
        ConstraintViolationException ex = new ConstraintViolationException("x", Set.of());
        ResponseEntity<RespuestaError> r = handler.handleConstraintViolation(ex, request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void cuerpoNoLegible() {
        ruta();
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("bad", null, null);
        ResponseEntity<RespuestaError> r = handler.handleNotReadable(ex, request);
        assertThat(r.getBody().codigo()).isEqualTo("SOLICITUD_INVALIDA");
    }

    @Test
    void tipoParametroIncorrecto() {
        ruta();
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "x", UUID.class, "id", null, null);
        ResponseEntity<RespuestaError> r = handler.handleTypeMismatch(ex, request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void parametroFaltante() {
        ruta();
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("fecha", "fecha");
        ResponseEntity<RespuestaError> r = handler.handleMissingParameter(ex, request);
        assertThat(r.getBody().error()).contains("fecha");
    }

    @Test
    void rutaInexistente() {
        ruta();
        ResponseEntity<RespuestaError> r = handler.handleNoResource(
                new org.springframework.web.servlet.resource.NoResourceFoundException(null, "/x", null), request);
        assertThat(r.getBody().codigo()).isEqualTo("RUTA_NO_ENCONTRADA");
    }

    @Test
    void metodoNoPermitido() {
        ruta();
        ResponseEntity<RespuestaError> r = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("DELETE"), request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void optimisticoLock() {
        ruta();
        ResponseEntity<RespuestaError> r = handler.handleOptimisticLock(
                new ObjectOptimisticLockingFailureException("x", null), request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void accesoDatosGenerico() {
        ruta();
        ResponseEntity<RespuestaError> r = handler.handleDataAccess(
                new DataIntegrityViolationException("db"), request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void accesoDatosSqlUnico() {
        ruta();
        ResponseEntity<RespuestaError> r = handler.handleDataAccess(
                new DataIntegrityViolationException("db", new SQLException("dup", "23505")), request);
        assertThat(r.getBody().codigo()).isEqualTo("REGISTRO_DUPLICADO");
    }

    @Test
    void accesoDatosSqlLlaveForanea() {
        ruta();
        ResponseEntity<RespuestaError> r = handler.handleDataAccess(
                new DataIntegrityViolationException("db", new SQLException("fk", "23503")), request);
        assertThat(r.getBody().codigo()).isEqualTo("REFERENCIA_INVALIDA");
    }

    @Test
    void accesoDatosSqlCheck() {
        ruta();
        ResponseEntity<RespuestaError> r = handler.handleDataAccess(
                new DataIntegrityViolationException("db", new SQLException("chk", "23514")), request);
        assertThat(r.getBody().codigo()).isEqualTo("TRANSICION_INVALIDA");
    }

    @Test
    void accesoDatosSqlRaise() {
        ruta();
        ResponseEntity<RespuestaError> r = handler.handleDataAccess(
                new DataIntegrityViolationException("db", new SQLException("r", "P0001")), request);
        assertThat(r.getBody().codigo()).isEqualTo("TRANSICION_INVALIDA");
    }

    @Test
    void sqlExcepcionDirecta() {
        ruta();
        ResponseEntity<RespuestaError> r = handler.handleSql(new SQLException("boom"), request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void excepcionGenerica() {
        ruta();
        ResponseEntity<RespuestaError> r = handler.handleGeneric(new RuntimeException("x"), request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void accesoDatosConCausaAnidada() {
        ruta();
        SQLException sql = new SQLException("dup", "23505");
        var causa = new RuntimeException(new OptimisticLockingFailureException("x", sql));
        ResponseEntity<RespuestaError> r = handler.handleDataAccess(
                new DataIntegrityViolationException("db", causa), request);
        assertThat(r.getBody().codigo()).isEqualTo("REGISTRO_DUPLICADO");
    }

    @Test
    void excepcionConCausa() {
        ruta();
        ResponseEntity<RespuestaError> r = handler.handleCostos(
                new NoEncontradoException("X", "msg", new RuntimeException("causa")), request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
