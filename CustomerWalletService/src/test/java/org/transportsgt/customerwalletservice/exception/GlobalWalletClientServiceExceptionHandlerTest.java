package org.transportsgt.customerwalletservice.exception;

import org.transportsgt.customerwalletservice.exception.FabricaRespuestaError;

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
import org.transportsgt.customerwalletservice.dto.request.RecargaRequest;
import org.transportsgt.customerwalletservice.models.Cartera;

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

class GlobalWalletClientServiceExceptionHandlerTest {

    private static final String RUTA = "/v1/customers/cartera/pagos";

    private GlobalWalletClientServiceExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalWalletClientServiceExceptionHandler(new FabricaRespuestaError());
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

    @Nested
    class HandleCustomerWallet {

        @Test
        void handleCustomerWallet_usaStatusYCodigoDeCadaExcepcion() {
            assertAll(
                    () -> assertRespuesta(handler.handleCustomerWallet(new ClienteNoEncontradoException(), request),
                            HttpStatus.NOT_FOUND, "NO_ENCONTRADO"),
                    () -> assertRespuesta(handler.handleCustomerWallet(new PagoNoEncontradoException(), request),
                            HttpStatus.NOT_FOUND, "NO_ENCONTRADO"),
                    () -> assertRespuesta(handler.handleCustomerWallet(new NitDuplicadoException(), request),
                            HttpStatus.CONFLICT, "NIT_DUPLICADO"),
                    () -> assertRespuesta(handler.handleCustomerWallet(new DpiDuplicadoException(), request),
                            HttpStatus.CONFLICT, "DPI_DUPLICADO"),
                    () -> assertRespuesta(handler.handleCustomerWallet(new ClaveIdempotenciaReutilizadaException(), request),
                            HttpStatus.CONFLICT, "CLAVE_IDEMPOTENCIA_REUTILIZADA"),
                    () -> assertRespuesta(handler.handleCustomerWallet(new SaldoInsuficienteException(), request),
                            HttpStatus.UNPROCESSABLE_CONTENT, "SALDO_INSUFICIENTE"),
                    () -> assertRespuesta(handler.handleCustomerWallet(new PerfilIncompletoException(), request),
                            HttpStatus.UNPROCESSABLE_CONTENT, "PERFIL_INCOMPLETO"),
                    () -> assertRespuesta(handler.handleCustomerWallet(new CuentaInactivaException(), request),
                            HttpStatus.FORBIDDEN, "CUENTA_INACTIVA"),
                    () -> assertRespuesta(handler.handleCustomerWallet(new NitInvalidoException(), request),
                            HttpStatus.BAD_REQUEST, "VALIDACION_FALLIDA"),
                    () -> assertRespuesta(handler.handleCustomerWallet(new ClaveIdempotenciaRequeridaException(), request),
                            HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA"),
                    () -> assertRespuesta(handler.handleCustomerWallet(new ClaveIdempotenciaInvalidaException(), request),
                            HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA"),
                    () -> assertRespuesta(handler.handleCustomerWallet(new RangoFechasInvalidoException(), request),
                            HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA")
            );
        }

        @Test
        void handleCustomerWallet_jerarquiaDeGenericas() {
            assertAll(
                    () -> assertInstanceOf(RecursoNoEncontradoException.class, new ClienteNoEncontradoException()),
                    () -> assertInstanceOf(ConflictoException.class, new NitDuplicadoException()),
                    () -> assertInstanceOf(ReglaNegocioException.class, new SaldoInsuficienteException()),
                    () -> assertInstanceOf(AccesoDenegadoException.class, new CuentaInactivaException()),
                    () -> assertInstanceOf(SolicitudInvalidaException.class, new NitInvalidoException()),
                    () -> assertEquals(HttpStatus.SERVICE_UNAVAILABLE, new ServicioNoDisponibleException(
                            "SERVICIO_NO_DISPONIBLE", "x") { }.getStatus())
            );
        }
    }

    @Nested
    class Validacion {

        @Test
        void handleValidation_concatenaErroresPorCampo() throws Exception {
            // Arrange
            BeanPropertyBindingResult resultado = new BeanPropertyBindingResult(new Object(), "request");
            resultado.addError(new FieldError("request", "monto", "no debe ser nulo"));
            resultado.addError(new FieldError("request", "fechaRecarga", "no debe ser nulo"));
            MethodParameter parametro = new MethodParameter(Object.class.getMethod("equals", Object.class), 0);

            // Act
            ResponseEntity<RespuestaError> respuesta = handler.handleValidation(
                    new MethodArgumentNotValidException(parametro, resultado), request);

            // Assert
            assertAll(
                    () -> assertRespuesta(respuesta, HttpStatus.BAD_REQUEST, "VALIDACION_FALLIDA"),
                    () -> assertEquals("monto: no debe ser nulo; fechaRecarga: no debe ser nulo",
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
                    validator.validate(new RecargaRequest(null, null)));

            // Act
            ResponseEntity<RespuestaError> respuesta = handler.handleConstraintViolation(ex, request);

            // Assert
            assertAll(
                    () -> assertRespuesta(respuesta, HttpStatus.BAD_REQUEST, "VALIDACION_FALLIDA"),
                    () -> assertTrue(respuesta.getBody().error().contains("monto")),
                    () -> assertTrue(respuesta.getBody().error().contains("fechaRecarga"))
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
                    new ObjectOptimisticLockingFailureException(Cartera.class, UUID.randomUUID()), request);

            // Assert
            assertAll(
                    () -> assertRespuesta(respuesta, HttpStatus.CONFLICT, "CONFLICTO_CONCURRENCIA"),
                    () -> assertEquals(GlobalWalletClientServiceExceptionHandler.MENSAJE_CONCURRENCIA,
                            respuesta.getBody().error())
            );
        }
    }

    @Nested
    class HandleDataAccess {

        @Test
        void handleDataAccess_unicoDevuelve409ConMensajePorRestriccion() {
            assertAll(
                    () -> assertEquals("Ya existe un cliente con ese NIT", errorSql(
                            "ERROR: duplicate key value violates unique constraint \"uq_cliente_nit\"", "23505")),
                    () -> assertEquals("Ya existe un cliente con ese DPI", errorSql("uq_cliente_dpi", "23505")),
                    () -> assertEquals("El usuario ya tiene un perfil de cliente", errorSql("cliente_id_usuario_key", "23505")),
                    () -> assertEquals("El cliente ya tiene una cartera", errorSql("cartera_id_cliente_key", "23505")),
                    () -> assertEquals("Ya existe un movimiento de ese tipo para la misma compra o alquiler",
                            errorSql("uq_movimiento_referencia", "23505")),
                    () -> assertEquals("La Clave-Idempotencia ya se usó en otra operación",
                            errorSql("movimiento_cartera_clave_idempotencia_key", "23505")),
                    () -> assertEquals("Ya existe un registro con esos datos", errorSql("otra", "23505")),
                    () -> assertEquals("Ya existe un registro con esos datos", errorSql(null, "23505")),
                    () -> assertRespuesta(handler.handleDataAccess(excepcionSql("x", "23505"), request),
                            HttpStatus.CONFLICT, "REGISTRO_DUPLICADO")
            );
        }

        @Test
        void handleDataAccess_checkDevuelve422ConMensajePorRestriccion() {
            assertAll(
                    () -> assertTrue(errorSql("violates check constraint \"cliente_nit_check\"", "23514")
                            .startsWith("El NIT debe tener")),
                    () -> assertEquals("El DPI debe tener exactamente 13 dígitos", errorSql("cliente_dpi_check", "23514")),
                    () -> assertEquals("El saldo de la cartera no puede quedar negativo",
                            errorSql("cartera_saldo_check", "23514")),
                    () -> assertEquals("El saldo de la cartera no puede quedar negativo",
                            errorSql("movimiento_cartera_saldo_posterior_check", "23514")),
                    () -> assertEquals("El monto debe ser mayor que cero",
                            errorSql("movimiento_cartera_monto_check", "23514")),
                    () -> assertEquals("Las recargas no llevan referencia; los pagos y reembolsos sí",
                            errorSql("ck_mov_referencia", "23514")),
                    () -> assertEquals("Los datos no cumplen las reglas de validación", errorSql(null, "23514")),
                    () -> assertRespuesta(handler.handleDataAccess(excepcionSql("cartera_saldo_check", "23514"), request),
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
            String detalle = "ERROR: Los registros de movimiento_cartera son inmutables: no se pueden modificar ni eliminar\n"
                    + "  Where: PL/pgSQL function fn_bloquear_modificacion() line 3 at RAISE";

            // Act
            ResponseEntity<RespuestaError> respuesta = handler.handleDataAccess(excepcionSql(detalle, "23000"), request);

            // Assert
            assertAll(
                    () -> assertRespuesta(respuesta, HttpStatus.UNPROCESSABLE_CONTENT, "REGLA_BASE_DATOS"),
                    () -> assertEquals("Los registros de movimiento_cartera son inmutables: no se pueden modificar ni eliminar",
                            respuesta.getBody().error()),
                    () -> assertEquals("Mensaje de trigger", errorSql("ERROR: Mensaje de trigger", "P0001"))
            );
        }

        @Test
        void mensajeTrigger_casosLimite() {
            assertAll(
                    () -> assertEquals("La operación viola una regla de datos",
                            GlobalWalletClientServiceExceptionHandler.mensajeTrigger(null)),
                    () -> assertEquals("La operación viola una regla de datos",
                            GlobalWalletClientServiceExceptionHandler.mensajeTrigger(" ")),
                    () -> assertEquals("Sin prefijo", GlobalWalletClientServiceExceptionHandler.mensajeTrigger("Sin prefijo"))
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
                    () -> assertEquals(GlobalWalletClientServiceExceptionHandler.MENSAJE_INTERNO, respuesta.getBody().error())
            );
        }
    }
}
