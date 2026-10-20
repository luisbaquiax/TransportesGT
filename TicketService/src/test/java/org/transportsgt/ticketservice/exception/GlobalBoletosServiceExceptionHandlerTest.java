package org.transportsgt.ticketservice.exception;

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
import org.transportsgt.ticketservice.dto.request.CompraRequest;
import org.transportsgt.ticketservice.models.Compra;

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

class GlobalBoletosServiceExceptionHandlerTest {

    private static final String RUTA = "/v1/tickets/boletos/compras";

    private GlobalBoletosServiceExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalBoletosServiceExceptionHandler();
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

    private ResponseEntity<RespuestaError> sql(String mensaje, String estado) {
        return handler.handleDataAccess(excepcionSql(mensaje, estado), request);
    }

    private String errorSql(String mensaje, String estado) {
        return sql(mensaje, estado).getBody().error();
    }

    private static DataIntegrityViolationException excepcionSql(String mensaje, String estado) {
        // Igual que en ejecución: la SQLException de PostgreSQL envuelta por Hibernate y por Spring
        return new DataIntegrityViolationException("could not execute statement",
                new RuntimeException("hibernate", new SQLException(mensaje, estado)));
    }

    @Nested
    class HandleBoletosService {

        @Test
        void handleBoletosService_usaStatusYCodigoDeCadaExcepcion() {
            assertAll(
                    () -> assertRespuesta(handler.handleBoletosService(new ViajeNoEncontradoException(), request),
                            HttpStatus.NOT_FOUND, "NO_ENCONTRADO"),
                    () -> assertRespuesta(handler.handleBoletosService(new CompraNoEncontradaException(), request),
                            HttpStatus.NOT_FOUND, "NO_ENCONTRADO"),
                    () -> assertRespuesta(handler.handleBoletosService(new CompraDuplicadaException(), request),
                            HttpStatus.CONFLICT, "COMPRA_DUPLICADA"),
                    () -> assertRespuesta(handler.handleBoletosService(new CompraAnuladaException("VIAJE_ELIMINADO"),
                            request), HttpStatus.CONFLICT, "COMPRA_ANULADA"),
                    () -> assertRespuesta(handler.handleBoletosService(new AsientoNoDisponibleException(List.of(12)),
                            request), HttpStatus.CONFLICT, "ASIENTO_NO_DISPONIBLE"),
                    () -> assertRespuesta(handler.handleBoletosService(new ViajeNoDisponibleException("El viaje ya salió"),
                            request), HttpStatus.CONFLICT, "VIAJE_NO_DISPONIBLE"),
                    () -> assertRespuesta(handler.handleBoletosService(new AsientoInvalidoException("x"), request),
                            HttpStatus.BAD_REQUEST, "VALIDACION_FALLIDA"),
                    () -> assertRespuesta(handler.handleBoletosService(new NitInvalidoException(), request),
                            HttpStatus.BAD_REQUEST, "VALIDACION_FALLIDA"),
                    () -> assertRespuesta(handler.handleBoletosService(new SinPermisoSucursalException(), request),
                            HttpStatus.FORBIDDEN, "SIN_PERMISO"),
                    () -> assertRespuesta(handler.handleBoletosService(new PerfilIncompletoException(), request),
                            HttpStatus.UNPROCESSABLE_CONTENT, "PERFIL_INCOMPLETO"),
                    () -> assertRespuesta(handler.handleBoletosService(new CuentaInactivaException(), request),
                            HttpStatus.FORBIDDEN, "CUENTA_INACTIVA"),
                    () -> assertRespuesta(handler.handleBoletosService(new PagoRechazadoException(
                            HttpStatus.UNPROCESSABLE_CONTENT, "SALDO_INSUFICIENTE", "Saldo insuficiente"), request),
                            HttpStatus.UNPROCESSABLE_CONTENT, "SALDO_INSUFICIENTE"),
                    () -> assertRespuesta(handler.handleBoletosService(new CarteraNoDisponibleException("timeout"),
                            request), HttpStatus.SERVICE_UNAVAILABLE, "SERVICIO_NO_DISPONIBLE")
            );
        }

        @Test
        void asientoNoDisponible_nombraLosAsientosOcupados() {
            assertAll(
                    () -> assertEquals("El asiento 12 ya está reservado o vendido",
                            new AsientoNoDisponibleException(List.of(12)).getMessage()),
                    () -> assertTrue(new AsientoNoDisponibleException(List.of(12, 13)).getMessage().startsWith("Los asientos")),
                    () -> assertNotNull(new AsientoNoDisponibleException().getMessage())
            );
        }

        @Test
        void handleBoletosService_jerarquiaDeGenericas() {
            assertAll(
                    () -> assertInstanceOf(RecursoNoEncontradoException.class, new ViajeNoEncontradoException()),
                    () -> assertInstanceOf(ConflictoException.class, new CompraDuplicadaException()),
                    () -> assertInstanceOf(ReglaNegocioException.class, new PerfilIncompletoException()),
                    () -> assertInstanceOf(AccesoDenegadoException.class, new SinPermisoSucursalException()),
                    () -> assertInstanceOf(SolicitudInvalidaException.class, new NitInvalidoException()),
                    () -> assertInstanceOf(ServicioNoDisponibleException.class, new CarteraNoDisponibleException("x"))
            );
        }
    }

    @Nested
    class Validacion {

        @Test
        void handleValidation_concatenaErroresPorCampo() throws Exception {
            // Arrange
            BeanPropertyBindingResult resultado = new BeanPropertyBindingResult(new Object(), "request");
            resultado.addError(new FieldError("request", "idViaje", "no debe ser nulo"));
            resultado.addError(new FieldError("request", "asientos", "no debe estar vacío"));
            MethodParameter parametro = new MethodParameter(Object.class.getMethod("equals", Object.class), 0);

            // Act
            ResponseEntity<RespuestaError> respuesta = handler.handleValidation(
                    new MethodArgumentNotValidException(parametro, resultado), request);

            // Assert
            assertAll(
                    () -> assertRespuesta(respuesta, HttpStatus.BAD_REQUEST, "VALIDACION_FALLIDA"),
                    () -> assertEquals("idViaje: no debe ser nulo; asientos: no debe estar vacío", respuesta.getBody().error())
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
                    validator.validate(new CompraRequest(null, null, null, null)));

            // Act
            ResponseEntity<RespuestaError> respuesta = handler.handleConstraintViolation(ex, request);

            // Assert
            assertAll(
                    () -> assertRespuesta(respuesta, HttpStatus.BAD_REQUEST, "VALIDACION_FALLIDA"),
                    () -> assertTrue(respuesta.getBody().error().contains("idViaje"))
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
                            UUID.class, "idViaje", parametro, null), request), HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA"),
                    () -> assertRespuesta(handler.handleMissingParameter(new MissingServletRequestParameterException(
                            "idViaje", "UUID"), request), HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA")
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
                    new ObjectOptimisticLockingFailureException(Compra.class, UUID.randomUUID()), request);

            // Assert
            assertAll(
                    () -> assertRespuesta(respuesta, HttpStatus.CONFLICT, "CONFLICTO_CONCURRENCIA"),
                    () -> assertEquals(GlobalBoletosServiceExceptionHandler.MENSAJE_CONCURRENCIA, respuesta.getBody().error())
            );
        }
    }

    @Nested
    class HandleDataAccess {

        @Test
        void handleDataAccess_unicoDevuelve409ConMensajeYCodigoPorRestriccion() {
            assertAll(
                    () -> assertRespuesta(sql("duplicate key value violates unique constraint \"uq_asiento_activo\"",
                            "23505"), HttpStatus.CONFLICT, "ASIENTO_NO_DISPONIBLE"),
                    () -> assertEquals("Uno de los asientos ya está reservado o vendido; elija otro",
                            errorSql("uq_asiento_activo", "23505")),
                    () -> assertEquals("Ya existe una compra con ese idCompra", errorSql("compra_pkey", "23505")),
                    () -> assertEquals("Se generó un código de boleto repetido; intente de nuevo",
                            errorSql("boleto_codigo_key", "23505")),
                    () -> assertEquals("Ya existe un registro con esos datos", errorSql(null, "23505")),
                    () -> assertRespuesta(sql("compra_pkey", "23505"), HttpStatus.CONFLICT, "REGISTRO_DUPLICADO")
            );
        }

        @Test
        void handleDataAccess_checkDevuelve422ConMensajePorRestriccion() {
            assertAll(
                    () -> assertEquals("Una compra confirmada debe tener fecha de pago", errorSql("ck_compra_pago", "23514")),
                    () -> assertEquals("Una compra en línea debe tener el usuario comprador",
                            errorSql("ck_compra_en_linea", "23514")),
                    () -> assertEquals("Los datos no cumplen las reglas de validación", errorSql(null, "23514")),
                    () -> assertRespuesta(sql("ck_compra_pago", "23514"), HttpStatus.UNPROCESSABLE_CONTENT,
                            "DATOS_INCONSISTENTES")
            );
        }

        @Test
        void handleDataAccess_llaveForaneaDevuelve409() {
            assertRespuesta(sql("fk", "23503"), HttpStatus.CONFLICT, "REFERENCIA_INVALIDA");
        }

        @Test
        void handleDataAccess_triggerDevuelve422ConMensajeLimpio() {
            // Arrange
            String detalle = "ERROR: El asiento 45 no existe: el bus tiene 40 asientos\n"
                    + "  Where: PL/pgSQL function fn_boleto_validar_asiento() line 9 at RAISE";

            // Act
            ResponseEntity<RespuestaError> respuesta = sql(detalle, "P0001");

            // Assert
            assertAll(
                    () -> assertRespuesta(respuesta, HttpStatus.UNPROCESSABLE_CONTENT, "REGLA_BASE_DATOS"),
                    () -> assertEquals("El asiento 45 no existe: el bus tiene 40 asientos", respuesta.getBody().error()),
                    () -> assertEquals("Bloqueado", errorSql("ERROR: Bloqueado", "23000"))
            );
        }

        @Test
        void mensajeTrigger_casosLimite() {
            assertAll(
                    () -> assertEquals("La operación viola una regla de datos",
                            GlobalBoletosServiceExceptionHandler.mensajeTrigger(null)),
                    () -> assertEquals("La operación viola una regla de datos",
                            GlobalBoletosServiceExceptionHandler.mensajeTrigger(" ")),
                    () -> assertEquals("Sin prefijo", GlobalBoletosServiceExceptionHandler.mensajeTrigger("Sin prefijo"))
            );
        }

        @Test
        void handleDataAccess_sinSqlConocido_devuelve500SinDetalles() {
            assertAll(
                    () -> assertRespuesta(handler.handleDataAccess(new DataIntegrityViolationException("sin causa"),
                            request), HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO"),
                    () -> assertRespuesta(sql("timeout", "57014"), HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO")
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
            ResponseEntity<RespuestaError> respuesta = handler.handleGeneric(new IllegalStateException("detalle"), request);

            // Assert
            assertAll(
                    () -> assertRespuesta(respuesta, HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO"),
                    () -> assertEquals(GlobalBoletosServiceExceptionHandler.MENSAJE_INTERNO, respuesta.getBody().error())
            );
        }
    }
}
