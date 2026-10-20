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

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalViajeExceptionHandlerTest {

    private final GlobalViajeExceptionHandler handler = new GlobalViajeExceptionHandler();

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
}
