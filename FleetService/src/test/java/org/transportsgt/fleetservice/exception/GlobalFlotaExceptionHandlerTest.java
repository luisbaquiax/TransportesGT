package org.transportsgt.fleetservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalFlotaExceptionHandlerTest {

    private final GlobalFlotaExceptionHandler handler = new GlobalFlotaExceptionHandler();

    private HttpServletRequest request() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/v1/fleet/buses");
        return request;
    }

    private static void verificar(ResponseEntity<RespuestaError> respuesta, HttpStatus status, String codigo) {
        assertEquals(status, respuesta.getStatusCode());
        assertEquals(status.value(), respuesta.getBody().status());
        assertEquals(codigo, respuesta.getBody().codigo());
        assertEquals("/v1/fleet/buses", respuesta.getBody().ruta());
    }

    @Test
    void excepcionesDeNegocioMapeanSuStatus() {
        verificar(handler.handleFlota(new BusNoEncontradoException(UUID.randomUUID()), request()),
                HttpStatus.NOT_FOUND, "BUS_NO_ENCONTRADO");
        verificar(handler.handleFlota(new PlacaDuplicadaException("P1"), request()),
                HttpStatus.CONFLICT, "PLACA_DUPLICADA");
        verificar(handler.handleFlota(new BusConViajesException(UUID.randomUUID()), request()),
                HttpStatus.CONFLICT, "BUS_CON_VIAJES");
        verificar(handler.handleFlota(new FotoInvalidaException("mala"), request()),
                HttpStatus.BAD_REQUEST, "FOTO_INVALIDA");
        verificar(handler.handleFlota(new ViajesNoDisponibleException(), request()),
                HttpStatus.SERVICE_UNAVAILABLE, "SERVICIO_NO_DISPONIBLE");
        verificar(handler.handleFlota(new OperacionNoAutorizadaException("no"), request()),
                HttpStatus.FORBIDDEN, "OPERACION_NO_AUTORIZADA");
    }

    @Test
    void errorDeValidacionDeSpringDevuelve400() {
        verificar(handler.handleNotReadable(
                        new org.springframework.http.converter.HttpMessageNotReadableException(
                                "x", mock(org.springframework.http.HttpInputMessage.class)), request()),
                HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA");
        verificar(handler.handleTypeMismatch(
                        new MethodArgumentTypeMismatchException("malo", Integer.class, "estado", null, null),
                        request()),
                HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA");
        verificar(handler.handleMethodNotSupported(
                        new HttpRequestMethodNotSupportedException("DELETE"), request()),
                HttpStatus.METHOD_NOT_ALLOWED, "METODO_NO_PERMITIDO");
        verificar(handler.handleNoResource(
                        new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "/v1/fleet", "/no"),
                        request()),
                HttpStatus.NOT_FOUND, "RUTA_NO_ENCONTRADA");
    }

    @Test
    void restriccionesDePostgresSeTraducen() {
        verificar(handler.handleDataAccess(conRestriccion("ERROR: duplicate key value violates unique constraint \"uq_bus_placa\"", "23505"), request()),
                HttpStatus.CONFLICT, "REGISTRO_DUPLICADO");
        verificar(handler.handleDataAccess(conRestriccion("fk", "23503"), request()),
                HttpStatus.CONFLICT, "REFERENCIA_INVALIDA");
        verificar(handler.handleDataAccess(conRestriccion("check", "23514"), request()),
                HttpStatus.UNPROCESSABLE_CONTENT, "DATOS_INCONSISTENTES");
        verificar(handler.handleDataAccess(conRestriccion(
                        "ERROR: Los registros de bus no se eliminan, solo se desactivan\nWhere: PL/pgSQL", "23000"),
                request()), HttpStatus.UNPROCESSABLE_CONTENT, "REGLA_BASE_DATOS");
        verificar(handler.handleDataAccess(conRestriccion("otro", "P0001"), request()),
                HttpStatus.UNPROCESSABLE_CONTENT, "REGLA_BASE_DATOS");
        verificar(handler.handleDataAccess(conRestriccion("desconocido", "99999"), request()),
                HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO");
    }

    private static DataIntegrityViolationException conRestriccion(String mensaje, String sqlState) {
        return new DataIntegrityViolationException("fallo",
                new SQLException(mensaje, sqlState));
    }

    @Test
    void erroresDeSeguridadYConcurrencia() {
        verificar(handler.handleAccessDenied(new AccessDeniedException("no"), request()),
                HttpStatus.FORBIDDEN, "ACCESO_DENEGADO");
        verificar(handler.handleAuthentication(new BadCredentialsException("malo"), request()),
                HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO");
        verificar(handler.handleOptimisticLock(
                        new org.springframework.orm.ObjectOptimisticLockingFailureException("bus", new Object()),
                request()), HttpStatus.CONFLICT, "CONFLICTO_CONCURRENCIA");
        verificar(handler.handleGeneric(new IllegalStateException("boom"), request()),
                HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO");
    }

    @Test
    void mensajeDeTriggerSeLimpia() {
        assertEquals("Los registros de bus no se eliminan",
                GlobalFlotaExceptionHandler.mensajeTrigger(
                        "ERROR: Los registros de bus no se eliminan\nWhere: PL/pgSQL function"));
        assertEquals("La operación viola una regla de datos",
                GlobalFlotaExceptionHandler.mensajeTrigger("  "));
        assertEquals("sin formato", GlobalFlotaExceptionHandler.mensajeTrigger("sin formato"));
    }

    @Test
    void validacionDeBeanDevuelve400() {
        MethodArgumentNotValidException excepcion = mock(MethodArgumentNotValidException.class);
        org.springframework.validation.BeanPropertyBindingResult resultado =
                new org.springframework.validation.BeanPropertyBindingResult(new Object(), "crearBus");
        resultado.addError(new org.springframework.validation.FieldError(
                "crearBus", "placa", "no puede estar vacío"));
        when(excepcion.getBindingResult()).thenReturn(resultado);

        verificar(handler.handleValidation(excepcion, request()),
                HttpStatus.BAD_REQUEST, "VALIDACION_FALLIDA");
    }
}
