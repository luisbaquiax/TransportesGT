package org.transportsgt.tripservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalViajeExceptionHandlerTest {

    private final GlobalViajeExceptionHandler handler = new GlobalViajeExceptionHandler(new FabricaRespuestaError());

    @Mock
    private HttpServletRequest request;

    private void uri() {
        when(request.getRequestURI()).thenReturn("/viajes");
    }

    @Test
    void viajeExceptionConservaStatusYCodigo() {
        uri();
        var resp = handler.handleViaje(new ReglaNegocioException("VIAJE_NO_ELIMINABLE", "ya inició"), request);
        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
        assertEquals("VIAJE_NO_ELIMINABLE", resp.getBody().codigo());
    }

    @Test
    void validacionDeCamposDevuelve400() {
        uri();
        var result = new BeanPropertyBindingResult(new Object(), "req");
        result.addError(new FieldError("req", "idRuta", "idRuta es obligatoria"));
        var ex = new MethodArgumentNotValidException(null, result);
        var resp = handler.handleValidation(ex, request);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("VALIDACION_FALLIDA", resp.getBody().codigo());
    }

    @Test
    void duplicadoDePostgresDevuelve409() {
        uri();
        SQLException sql = new SQLException("dup", "23505");
        var resp = handler.handleDataAccess(new DuplicateKeyException("dup", sql), request);
        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
        assertEquals("REGISTRO_DUPLICADO", resp.getBody().codigo());
    }

    @Test
    void exclusionDePostgresDevuelve409Traslape() {
        uri();
        SQLException sql = new SQLException("overlapping constraint ex_traslape_bus", "23P01");
        var resp = handler.handleDataAccess(new DataIntegrityViolationException("x", sql), request);
        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
        assertEquals("CONFLICTO_HORARIO", resp.getBody().codigo());
    }

    @Test
    void raiseDeTriggerDeEliminacionDevuelve409() {
        uri();
        SQLException sql = new SQLException(
                "ERROR: El viaje ya inició o finalizado y no se puede eliminar", "P0001");
        var resp = handler.handleDataAccess(new DataIntegrityViolationException("x", sql), request);
        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
        assertEquals("VIAJE_NO_ELIMINABLE", resp.getBody().codigo());
    }

    @Test
    void raiseDeTriggerDeRutaConViajesDevuelve409() {
        uri();
        SQLException sql = new SQLException(
                "ERROR: La ruta no se puede eliminar porque tiene viajes asociados", "P0001");
        var resp = handler.handleDataAccess(new DataIntegrityViolationException("x", sql), request);
        assertEquals("RUTA_CON_VIAJES", resp.getBody().codigo());
    }

    @Test
    void optimistLockDevuelve409() {
        uri();
        var resp = handler.handleOptimisticLock(
                new ObjectOptimisticLockingFailureException("viaje", 1L), request);
        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
        assertEquals("CONFLICTO_CONCURRENCIA", resp.getBody().codigo());
    }

    @Test
    void errorGenericoDevuelve500() {
        uri();
        var resp = handler.handleGeneric(new RuntimeException("boom"), request);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
        assertEquals("ERROR_INTERNO", resp.getBody().codigo());
    }

    @Test
    void servicioNoDisponibleConserva503() {
        uri();
        var resp = handler.handleViaje(
                new ServicioNoDisponibleException("Boletos no responde"), request);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, resp.getStatusCode());
        assertEquals("SERVICIO_NO_DISPONIBLE", resp.getBody().codigo());
    }

    @Test
    void noAutorizadaConserva403() {
        uri();
        var resp = handler.handleViaje(
                new OperacionNoAutorizadaException("solo su sucursal"), request);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    void solicitudInvalidaConserva400() {
        uri();
        var resp = handler.handleViaje(
                new SolicitudInvalidaException("llegada antes de salida"), request);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void viajeNoEncontradoConserva404() {
        uri();
        var resp = handler.handleViaje(
                new ViajeNoEncontradoException(java.util.UUID.randomUUID()), request);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    private String codigoSql(String mensaje, String estado) {
        uri();
        SQLException sql = new SQLException(mensaje, estado);
        var resp = handler.handleDataAccess(new DataIntegrityViolationException("x", sql), request);
        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
        return resp.getBody().codigo();
    }

    @Test
    void llaveForaneaDePostgresDevuelve409() {
        assertEquals("REGISTRO_RELACIONADO", codigoSql("fk", "23503"));
    }

    @Test
    void checkDePostgresSinTraslapeDevuelveMensajeGenerico() {
        uri();
        var resp = handler.handleDataAccess(
                new DataIntegrityViolationException("x", new SQLException(null, "23514")), request);
        assertEquals("CONFLICTO_HORARIO", resp.getBody().codigo());
        assertEquals("La operación viola una regla de integridad de datos", resp.getBody().error());
    }

    @Test
    void raiseDeTriggerMapeaElCodigoPorMensaje() {
        assertEquals("VIAJE_NO_ELIMINABLE", codigoSql("El viaje no ha iniciado ni finalizado", "P0001"));
        assertEquals("VIAJE_NO_ELIMINABLE", codigoSql("Ya inició", "P0001"));
        assertEquals("VIAJE_NO_ELIMINABLE", codigoSql("viaje iniciado o completado", "P0001"));
        assertEquals("CONFLICTO_HORARIO", codigoSql("ERROR: CONFLICTO_HORARIO con otro viaje", "P0001"));
        assertEquals("CONFLICTO_HORARIO", codigoSql("el bus se traslapa", "P0001"));
        assertEquals("CAPACIDAD_INSUFICIENTE", codigoSql("CAPACIDAD_INSUFICIENTE para el viaje", "P0001"));
        assertEquals("REGLA_NEGOCIO", codigoSql("otra regla", "P0001"));
        assertEquals("REGLA_NEGOCIO", codigoSql(null, "P0001"));
    }

    @Test
    void raiseDeTriggerQuitaSoloElPrefijoError() {
        uri();
        var resp = handler.handleDataAccess(new DataIntegrityViolationException("x",
                new SQLException("ERROR: El viaje ya inició", "P0001")), request);
        assertEquals("El viaje ya inició", resp.getBody().error());
    }

    @Test
    void raiseSinMensajeUsaTextoPorDefecto() {
        uri();
        var resp = handler.handleDataAccess(
                new DataIntegrityViolationException("x", new SQLException(null, "P0001")), request);
        assertEquals("Regla de negocio violada", resp.getBody().error());
    }

    @Test
    void errorDeDatosSinSqlStateConocidoDevuelve500() {
        uri();
        var resp = handler.handleDataAccess(
                new DataIntegrityViolationException("x", new SQLException("otro", "42000")), request);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
        assertEquals("ERROR_INTERNO", resp.getBody().codigo());
    }

    @Test
    void validacionSinErroresDeCampoUsaMensajeGenerico() throws Exception {
        uri();
        var resultado = new BeanPropertyBindingResult(new Object(), "obj");
        var ex = new MethodArgumentNotValidException(
                new org.springframework.core.MethodParameter(Object.class.getMethod("toString"), -1), resultado);
        var resp = handler.handleValidation(ex, request);
        assertEquals("Error de validación", resp.getBody().error());
    }

    @Test
    void validacionDeMetodoSinResultadosUsaMensajeGenerico() {
        uri();
        HandlerMethodValidationException ex = mock(HandlerMethodValidationException.class);
        when(ex.getParameterValidationResults()).thenReturn(List.of());
        var resp = handler.handleMethodValidation(ex, request);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("Error de validación", resp.getBody().error());
    }

    @Test
    @SuppressWarnings("unchecked")
    void violacionDeRestriccionesOrdenaLosMensajes() {
        uri();
        ConstraintViolation<Object> b = mock(ConstraintViolation.class);
        ConstraintViolation<Object> a = mock(ConstraintViolation.class);
        Path rutaB = mock(Path.class);
        Path rutaA = mock(Path.class);
        when(rutaB.toString()).thenReturn("listar.size");
        when(rutaA.toString()).thenReturn("listar.page");
        when(b.getPropertyPath()).thenReturn(rutaB);
        when(a.getPropertyPath()).thenReturn(rutaA);
        when(b.getMessage()).thenReturn("máximo 100");
        when(a.getMessage()).thenReturn("mínimo 0");
        var resp = handler.handleConstraintViolation(new ConstraintViolationException(Set.of(b, a)), request);
        assertEquals("listar.page: mínimo 0; listar.size: máximo 100", resp.getBody().error());
    }

    @Test
    void erroresDeLaSolicitudDevuelven400() {
        uri();
        assertEquals("SOLICITUD_INVALIDA", handler.handleNotReadable(
                mock(HttpMessageNotReadableException.class), request).getBody().codigo());

        MethodArgumentTypeMismatchException tipo = mock(MethodArgumentTypeMismatchException.class);
        when(tipo.getName()).thenReturn("id");
        assertEquals("El parámetro 'id' tiene un valor inválido",
                handler.handleTypeMismatch(tipo, request).getBody().error());

        assertEquals("Falta el parámetro 'fecha'", handler.handleMissingParameter(
                new MissingServletRequestParameterException("fecha", "LocalDate"), request).getBody().error());
    }

    @Test
    void rutaInexistenteYMetodoNoPermitido() {
        uri();
        assertEquals(HttpStatus.NOT_FOUND, handler.handleNoResource(
                new NoResourceFoundException(HttpMethod.GET, "/x", "/x"), request).getStatusCode());
        var resp = handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("PATCH"), request);
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, resp.getStatusCode());
        assertEquals("METODO_NO_PERMITIDO", resp.getBody().codigo());
    }
}
