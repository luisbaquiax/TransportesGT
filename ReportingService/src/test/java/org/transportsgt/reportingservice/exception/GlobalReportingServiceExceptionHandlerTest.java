package org.transportsgt.reportingservice.exception;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import jakarta.validation.constraints.NotNull;
import org.transportsgt.reportingservice.models.RepIngreso;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalReportingServiceExceptionHandlerTest {

    private static final String RUTA = "/v1/reports/reportes/ingresos";

    private GlobalReportingServiceExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalReportingServiceExceptionHandler();
        request = new MockHttpServletRequest("POST", RUTA);
    }

    private static void assertRespuesta(ResponseEntity<RespuestaError> respuesta, HttpStatus status, String codigo) {
        RespuestaError cuerpo = respuesta.getBody();
        assertAll(
                () -> assertEquals(status, respuesta.getStatusCode()),
                () -> assertNotNull(cuerpo),
                () -> assertEquals(status.value(), cuerpo.status()),
                () -> assertEquals(codigo, cuerpo.codigo()),
                () -> assertEquals(RUTA, cuerpo.ruta()),
                () -> assertNotNull(cuerpo.timestamp())
        );
    }

    private String errorSql(String mensaje, String estado) {
        return handler.handleDataAccess(excepcionSql(mensaje, estado), request).getBody().error();
    }

    private static DataIntegrityViolationException excepcionSql(String mensaje, String estado) {
        // Igual que en ejecución: la SQLException de PostgreSQL envuelta por Hibernate y por Spring
        return new DataIntegrityViolationException("could not execute statement",
                new RuntimeException("hibernate", new SQLException(mensaje, estado)));
    }

    /** Bean de prueba para generar violaciones de restricciones. */
    record Filtro(@NotNull String desde, @NotNull String hasta) {
    }

    @Nested
    class HandleReportingService {

        @Test
        void handleReportingService_usaStatusYCodigoDeCadaExcepcion() {
            UUID id = UUID.randomUUID();
            assertAll(
                    () -> assertRespuesta(handler.handleReportingService(new IntervaloInvalidoException(), request),
                            HttpStatus.BAD_REQUEST, "VALIDACION"),
                    () -> assertRespuesta(handler.handleReportingService(new FormatoNoSoportadoException("csv"), request),
                            HttpStatus.BAD_REQUEST, "VALIDACION"),
                    () -> assertRespuesta(handler.handleReportingService(new SucursalRequeridaException(), request),
                            HttpStatus.BAD_REQUEST, "VALIDACION"),
                    () -> assertRespuesta(handler.handleReportingService(new SucursalNoEncontradaException(id), request),
                            HttpStatus.NOT_FOUND, "NO_ENCONTRADO"),
                    () -> assertRespuesta(handler.handleReportingService(new BusNoEncontradoException(id), request),
                            HttpStatus.NOT_FOUND, "NO_ENCONTRADO"),
                    () -> assertRespuesta(handler.handleReportingService(new RutaNoEncontradaException(id), request),
                            HttpStatus.NOT_FOUND, "NO_ENCONTRADO"),
                    () -> assertRespuesta(handler.handleReportingService(new SinPermisoSucursalException(), request),
                            HttpStatus.FORBIDDEN, "SIN_PERMISO"),
                    () -> assertRespuesta(handler.handleReportingService(new ProyeccionNoDisponibleException("x"),
                            request), HttpStatus.SERVICE_UNAVAILABLE, "PROYECCION_NO_DISPONIBLE")
            );
        }

        @Test
        void handleReportingService_jerarquiaDeGenericas() {
            assertAll(
                    () -> assertInstanceOf(SolicitudInvalidaException.class, new IntervaloInvalidoException()),
                    () -> assertInstanceOf(RecursoNoEncontradoException.class, new BusNoEncontradoException(null)),
                    () -> assertInstanceOf(AccesoDenegadoException.class, new SinPermisoSucursalException()),
                    () -> assertInstanceOf(ReportingServiceException.class, new ProyeccionNoDisponibleException("x"))
            );
        }
    }

    @Nested
    class Validacion {

        @Test
        void handleValidation_concatenaErroresPorCampo() throws Exception {
            // Arrange
            BeanPropertyBindingResult resultado = new BeanPropertyBindingResult(new Object(), "request");
            resultado.addError(new FieldError("request", "desde", "no debe ser nulo"));
            resultado.addError(new FieldError("request", "hasta", "no debe ser nulo"));
            MethodParameter parametro = new MethodParameter(Object.class.getMethod("equals", Object.class), 0);

            // Act
            ResponseEntity<RespuestaError> respuesta = handler.handleValidation(
                    new MethodArgumentNotValidException(parametro, resultado), request);

            // Assert
            assertAll(
                    () -> assertRespuesta(respuesta, HttpStatus.BAD_REQUEST, "VALIDACION_FALLIDA"),
                    () -> assertEquals("desde: no debe ser nulo; hasta: no debe ser nulo",
                            respuesta.getBody().error())
            );
        }

        @Test
        void handleMethodValidation_devuelve400ConElParametro() {
            // Arrange
            HandlerMethodValidationException ex = mock(HandlerMethodValidationException.class);
            ParameterValidationResult resultado = mock(ParameterValidationResult.class);
            MethodParameter parametro = mock(MethodParameter.class);
            when(parametro.getParameterName()).thenReturn("tamano");
            when(resultado.getMethodParameter()).thenReturn(parametro);
            when(resultado.getResolvableErrors()).thenReturn(List.of(
                    new DefaultMessageSourceResolvable(null, null, "debe ser menor que o igual a 100")));
            when(ex.getParameterValidationResults()).thenReturn(List.of(resultado));

            // Act
            ResponseEntity<RespuestaError> respuesta = handler.handleMethodValidation(ex, request);

            // Assert
            assertAll(
                    () -> assertRespuesta(respuesta, HttpStatus.BAD_REQUEST, "VALIDACION_FALLIDA"),
                    () -> assertEquals("tamano: debe ser menor que o igual a 100", respuesta.getBody().error())
            );
        }

        @Test
        void handleMethodValidation_sinDetalles_usaMensajeGenerico() {
            // Arrange
            HandlerMethodValidationException ex = mock(HandlerMethodValidationException.class);
            when(ex.getParameterValidationResults()).thenReturn(List.of());

            // Act & Assert
            assertEquals("Error de validación", handler.handleMethodValidation(ex, request).getBody().error());
        }

        @Test
        void handleConstraintViolation_devuelve400() {
            // Arrange
            Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
            ConstraintViolationException ex = new ConstraintViolationException(
                    validator.validate(new Filtro(null, null)));

            // Act
            ResponseEntity<RespuestaError> respuesta = handler.handleConstraintViolation(ex, request);

            // Assert
            assertAll(
                    () -> assertRespuesta(respuesta, HttpStatus.BAD_REQUEST, "VALIDACION_FALLIDA"),
                    () -> assertTrue(respuesta.getBody().error().contains("desde")),
                    () -> assertTrue(respuesta.getBody().error().contains("hasta"))
            );
        }
    }

    @Nested
    class Solicitud {

        @Test
        void erroresDeLectura_devuelven400() throws Exception {
            MethodParameter parametro = new MethodParameter(Object.class.getMethod("equals", Object.class), 0);
            assertAll(
                    () -> assertRespuesta(handler.handleNotReadable(new HttpMessageNotReadableException("json",
                            new MockHttpInputMessage(new byte[0])), request), HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA"),
                    () -> assertRespuesta(handler.handleTypeMismatch(new MethodArgumentTypeMismatchException("abc",
                            UUID.class, "idReferencia", parametro, null), request), HttpStatus.BAD_REQUEST,
                            "SOLICITUD_INVALIDA"),
                    () -> assertRespuesta(handler.handleMissingParameter(new MissingServletRequestParameterException(
                            "idReferencia", "UUID"), request), HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA")
            );
        }

        @Test
        void rutaYMetodo_devuelven404Y405() {
            assertAll(
                    () -> assertRespuesta(handler.handleNoResource(new NoResourceFoundException(HttpMethod.GET, RUTA, "x"),
                            request), HttpStatus.NOT_FOUND, "RUTA_NO_ENCONTRADA"),
                    () -> assertRespuesta(handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException(
                            "DELETE"), request), HttpStatus.METHOD_NOT_ALLOWED, "METODO_NO_PERMITIDO")
            );
        }

        @Test
        void optimisticLock_devuelve409ConMensajeLegible() {
            // Act
            ResponseEntity<RespuestaError> respuesta = handler.handleOptimisticLock(
                    new ObjectOptimisticLockingFailureException(RepIngreso.class, UUID.randomUUID()), request);

            // Assert
            assertAll(
                    () -> assertRespuesta(respuesta, HttpStatus.CONFLICT, "CONFLICTO_CONCURRENCIA"),
                    () -> assertEquals(GlobalReportingServiceExceptionHandler.MENSAJE_CONCURRENCIA,
                            respuesta.getBody().error())
            );
        }
    }

    @Nested
    class HandleDataAccess {

        @Test
        void handleDataAccess_unicoDevuelve409ConMensajePorRestriccion() {
            assertAll(
                    () -> assertEquals("Ya existe un ingreso para esa compra o alquiler", errorSql(
                            "ERROR: duplicate key value violates unique constraint \"uq_rep_ingreso\"", "23505")),
                    () -> assertEquals("Ya existe un gasto con esa referencia", errorSql("uq_rep_gasto", "23505")),
                    () -> assertEquals("Ya existe un registro con esos datos", errorSql("otra", "23505")),
                    () -> assertEquals("Ya existe un registro con esos datos", errorSql(null, "23505")),
                    () -> assertRespuesta(handler.handleDataAccess(excepcionSql("x", "23505"), request),
                            HttpStatus.CONFLICT, "REGISTRO_DUPLICADO")
            );
        }

        @Test
        void handleDataAccess_checkDevuelve422ConMensajePorRestriccion() {
            assertAll(
                    () -> assertTrue(errorSql("rep_ingreso_tipo_ingreso_check", "23514").startsWith("El tipo de ingreso")),
                    () -> assertTrue(errorSql("rep_gasto_tipo_gasto_check", "23514").startsWith("El tipo de gasto")),
                    () -> assertTrue(errorSql("rep_gasto_tipo_linea_check", "23514").startsWith("El tipo de línea")),
                    () -> assertEquals("Los datos no cumplen las reglas de validación", errorSql(null, "23514")),
                    () -> assertRespuesta(handler.handleDataAccess(excepcionSql("x", "23514"), request),
                            HttpStatus.UNPROCESSABLE_CONTENT, "DATOS_INCONSISTENTES")
            );
        }

        @Test
        void handleDataAccess_llaveForaneaDevuelve409() {
            assertRespuesta(handler.handleDataAccess(excepcionSql("fk", "23503"), request),
                    HttpStatus.CONFLICT, "REFERENCIA_INVALIDA");
        }

        @Test
        void handleDataAccess_triggerDevuelve422ConMensajeLimpio() {
            // Arrange
            String detalle = "ERROR: Regla de datos violada\n"
                    + "  Where: PL/pgSQL function fn() line 3 at RAISE";

            // Act
            ResponseEntity<RespuestaError> respuesta = handler.handleDataAccess(excepcionSql(detalle, "23000"), request);

            // Assert
            assertAll(
                    () -> assertRespuesta(respuesta, HttpStatus.UNPROCESSABLE_CONTENT, "REGLA_BASE_DATOS"),
                    () -> assertEquals("Regla de datos violada", respuesta.getBody().error()),
                    () -> assertEquals("Mensaje de trigger", errorSql("ERROR: Mensaje de trigger", "P0001"))
            );
        }

        @Test
        void mensajeTrigger_casosLimite() {
            assertAll(
                    () -> assertEquals("La operación viola una regla de datos",
                            handler.mensajeTrigger(null)),
                    () -> assertEquals("La operación viola una regla de datos",
                            handler.mensajeTrigger(" ")),
                    () -> assertEquals("Sin prefijo", handler.mensajeTrigger("Sin prefijo"))
            );
        }

        @Test
        void handleDataAccess_sinSqlConocido_devuelve500SinDetalles() {
            assertAll(
                    () -> assertRespuesta(handler.handleDataAccess(new DataIntegrityViolationException("sin causa"),
                            request), HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO"),
                    () -> assertRespuesta(handler.handleDataAccess(excepcionSql("timeout", "57014"), request),
                            HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO")
            );
        }
    }

    @Nested
    class Seguridad {

        @Test
        void seguridad_devuelve403Y401() {
            assertAll(
                    () -> assertRespuesta(handler.handleAccessDenied(new AuthorizationDeniedException("Access Denied"),
                            request), HttpStatus.FORBIDDEN, "ACCESO_DENEGADO"),
                    () -> assertRespuesta(handler.handleAuthentication(new BadCredentialsException("x"), request),
                            HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO")
            );
        }

        @Test
        void generica_devuelve500SinExponerElMensaje() {
            // Act
            ResponseEntity<RespuestaError> respuesta = handler.handleGeneric(
                    new IllegalStateException("detalle interno"), request);

            // Assert
            assertAll(
                    () -> assertRespuesta(respuesta, HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO"),
                    () -> assertEquals(GlobalReportingServiceExceptionHandler.MENSAJE_INTERNO, respuesta.getBody().error())
            );
        }
    }
}
