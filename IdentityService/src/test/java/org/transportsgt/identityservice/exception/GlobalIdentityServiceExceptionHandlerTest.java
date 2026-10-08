package org.transportsgt.identityservice.exception;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
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
import org.transportsgt.identityservice.dto.request.LoginRequest;
import org.transportsgt.identityservice.models.Usuario;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalIdentityServiceExceptionHandlerTest {

    private static final String RUTA = "/v1/identity/usuarios";

    private final GlobalIdentityServiceExceptionHandler handler = new GlobalIdentityServiceExceptionHandler();
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
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

    @Test
    void identityService_usaStatusYCodigoDeLaExcepcion() {
        assertAll(
                () -> assertRespuesta(handler.handleIdentityService(new UsuarioNoEncontradoException(UUID.randomUUID()),
                        request), HttpStatus.NOT_FOUND, "USUARIO_NO_ENCONTRADO"),
                () -> assertRespuesta(handler.handleIdentityService(new CorreoDuplicadoException("a@b.gt"), request),
                        HttpStatus.CONFLICT, "CORREO_DUPLICADO"),
                () -> assertRespuesta(handler.handleIdentityService(new UltimoAdministradorSistemaException(), request),
                        HttpStatus.UNPROCESSABLE_CONTENT, "ULTIMO_ADMIN_SISTEMA"),
                () -> assertRespuesta(handler.handleIdentityService(new CredencialesInvalidasException(), request),
                        HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS"),
                () -> assertRespuesta(handler.handleIdentityService(new UsuarioInactivoException(), request),
                        HttpStatus.FORBIDDEN, "USUARIO_INACTIVO"),
                () -> assertRespuesta(handler.handleIdentityService(new TokenContrasenaInvalidoException(), request),
                        HttpStatus.GONE, "TOKEN_CONTRASENA_INVALIDO")
        );
    }

    @Test
    void identityService_jerarquiaDeGenericas() {
        IdentityServiceException[] excepciones = {
                new SucursalNoEncontradaException(UUID.randomUUID()),
                new NombreSucursalDuplicadoException("Xela"),
                new UltimoAdministradorSucursalException(),
                new ContrasenaActualIncorrectaException(),
                new ContrasenaRepetidaException(),
                new RolNoPermitidoException("x"),
                new SucursalNoPermitidaException("x"),
                new SucursalInactivaException(UUID.randomUUID()),
                new ActivacionNoPendienteException(),
                new TokenJwtInvalidoException(),
                new OperacionNoAutorizadaException("x"),
                new RegistroModificadoException()
        };
        for (IdentityServiceException ex : excepciones) {
            ResponseEntity<RespuestaError> respuesta = handler.handleIdentityService(ex, request);
            assertEquals(ex.getStatus(), respuesta.getStatusCode());
            assertEquals(ex.getMessage(), respuesta.getBody().error());
        }
        assertAll(
                () -> assertTrue(new SucursalNoEncontradaException(UUID.randomUUID()) instanceof RecursoNoEncontradoException),
                () -> assertTrue(new CorreoDuplicadoException("x") instanceof ConflictoException),
                () -> assertTrue(new ContrasenaRepetidaException() instanceof ReglaNegocioException),
                () -> assertTrue(new CredencialesInvalidasException() instanceof NoAutenticadoException),
                () -> assertTrue(new OperacionNoAutorizadaException("x") instanceof AccesoDenegadoException),
                () -> assertTrue(new TokenContrasenaInvalidoException() instanceof RecursoExpiradoException)
        );
    }

    @Test
    void methodArgumentNotValid_concatenaErroresPorCampo() throws Exception {
        // Arrange
        BeanPropertyBindingResult resultado = new BeanPropertyBindingResult(new Object(), "request");
        resultado.addError(new FieldError("request", "correo", "no es válido"));
        resultado.addError(new FieldError("request", "nombre", "no debe estar vacío"));
        MethodParameter parametro = new MethodParameter(Object.class.getMethod("equals", Object.class), 0);

        // Act
        ResponseEntity<RespuestaError> respuesta = handler.handleValidation(
                new MethodArgumentNotValidException(parametro, resultado), request);

        // Assert
        assertAll(
                () -> assertRespuesta(respuesta, HttpStatus.BAD_REQUEST, "VALIDACION_FALLIDA"),
                () -> assertEquals("correo: no es válido; nombre: no debe estar vacío", respuesta.getBody().error())
        );
    }

    @Test
    void handlerMethodValidation_devuelve400ConElParametro() throws Exception {
        // Arrange
        HandlerMethodValidationException ex = mock(HandlerMethodValidationException.class);
        ParameterValidationResult resultado = mock(ParameterValidationResult.class);
        MethodParameter parametro = mock(MethodParameter.class);
        when(parametro.getParameterName()).thenReturn("token");
        when(resultado.getMethodParameter()).thenReturn(parametro);
        when(resultado.getResolvableErrors()).thenReturn(List.of(
                new DefaultMessageSourceResolvable(null, null, "no debe estar vacío")));
        when(ex.getParameterValidationResults()).thenReturn(List.of(resultado));

        // Act
        ResponseEntity<RespuestaError> respuesta = handler.handleMethodValidation(ex, request);

        // Assert
        assertAll(
                () -> assertRespuesta(respuesta, HttpStatus.BAD_REQUEST, "VALIDACION_FALLIDA"),
                () -> assertEquals("token: no debe estar vacío", respuesta.getBody().error())
        );
    }

    @Test
    void handlerMethodValidation_sinDetalles_usaMensajeGenerico() {
        // Arrange
        HandlerMethodValidationException ex = mock(HandlerMethodValidationException.class);
        when(ex.getParameterValidationResults()).thenReturn(List.of());

        // Assert
        assertEquals("Error de validación", handler.handleMethodValidation(ex, request).getBody().error());
    }

    @Test
    void constraintViolation_devuelve400() {
        // Arrange
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        ConstraintViolationException ex = new ConstraintViolationException(
                validator.validate(new LoginRequest("no-es-correo", "")));

        // Act
        ResponseEntity<RespuestaError> respuesta = handler.handleConstraintViolation(ex, request);

        // Assert
        assertAll(
                () -> assertRespuesta(respuesta, HttpStatus.BAD_REQUEST, "VALIDACION_FALLIDA"),
                () -> assertTrue(respuesta.getBody().error().contains("contrasena")),
                () -> assertTrue(respuesta.getBody().error().contains("correo"))
        );
    }

    @Test
    void erroresDeLectura_devuelven400() throws Exception {
        MethodParameter parametro = new MethodParameter(Object.class.getMethod("equals", Object.class), 0);
        assertAll(
                () -> assertRespuesta(handler.handleNotReadable(new HttpMessageNotReadableException("json",
                        new MockHttpInputMessage(new byte[0])), request), HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA"),
                () -> assertRespuesta(handler.handleTypeMismatch(new MethodArgumentTypeMismatchException("abc",
                        UUID.class, "id", parametro, null), request), HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA"),
                () -> assertRespuesta(handler.handleMissingParameter(new MissingServletRequestParameterException(
                        "token", "String"), request), HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA")
        );
    }

    @Test
    void rutaYMetodo_devuelven404Y405() {
        assertAll(
                () -> assertRespuesta(handler.handleNoResource(new NoResourceFoundException(HttpMethod.GET, RUTA, "x"),
                        request), HttpStatus.NOT_FOUND, "RUTA_NO_ENCONTRADA"),
                () -> assertRespuesta(handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("DELETE"),
                        request), HttpStatus.METHOD_NOT_ALLOWED, "METODO_NO_PERMITIDO")
        );
    }

    @Test
    void optimisticLock_devuelve409ConMensajeLegible() {
        // Act
        ResponseEntity<RespuestaError> respuesta = handler.handleOptimisticLock(
                new ObjectOptimisticLockingFailureException(Usuario.class, UUID.randomUUID()), request);

        // Assert
        assertAll(
                () -> assertRespuesta(respuesta, HttpStatus.CONFLICT, "CONFLICTO_CONCURRENCIA"),
                () -> assertEquals(GlobalIdentityServiceExceptionHandler.MENSAJE_CONCURRENCIA, respuesta.getBody().error())
        );
    }

    @Test
    void dataAccess_unicoDevuelve409ConMensajePorRestriccion() {
        assertAll(
                () -> assertEquals("Ya existe un usuario con ese correo", errorSql(
                        "ERROR: duplicate key value violates unique constraint \"uq_usuario_correo\"", "23505")),
                () -> assertEquals("Ya existe una sucursal con ese nombre", errorSql(
                        "ERROR: llave duplicada viola restricción de unicidad «sucursal_nombre_key»", "23505")),
                () -> assertEquals("Ya hay un enlace vigente para este usuario; intente de nuevo", errorSql(
                        "uq_token_activo", "23505")),
                () -> assertEquals("Ya existe un registro con esos datos", errorSql("otra", "23505")),
                () -> assertRespuesta(handler.handleDataAccess(excepcionSql("x", "23505"), request),
                        HttpStatus.CONFLICT, "REGISTRO_DUPLICADO")
        );
    }

    @Test
    void dataAccess_llaveForaneaDevuelve409() {
        assertRespuesta(handler.handleDataAccess(excepcionSql("fk", "23503"), request),
                HttpStatus.CONFLICT, "REFERENCIA_INVALIDA");
    }

    @Test
    void dataAccess_checkDevuelve422() {
        assertAll(
                () -> assertRespuesta(handler.handleDataAccess(excepcionSql("ck_usuario_sucursal", "23514"), request),
                        HttpStatus.UNPROCESSABLE_CONTENT, "DATOS_INCONSISTENTES"),
                () -> assertTrue(errorSql("violates check constraint \"ck_usuario_sucursal\"", "23514")
                        .startsWith("Los usuarios ADMIN_SUCURSAL")),
                () -> assertEquals("Los datos no cumplen las reglas de validación", errorSql("otro check", "23514"))
        );
    }

    @Test
    void dataAccess_triggerDevuelve422ConMensajeLimpio() {
        // Arrange
        String detalle = "ERROR: Debe existir al menos un administrador de sistema activo\n"
                + "  Where: PL/pgSQL function fn_proteger_ultimo_admin() line 7 at RAISE";

        // Act
        ResponseEntity<RespuestaError> respuesta = handler.handleDataAccess(excepcionSql(detalle, "P0001"), request);

        // Assert
        assertAll(
                () -> assertRespuesta(respuesta, HttpStatus.UNPROCESSABLE_CONTENT, "REGLA_BASE_DATOS"),
                () -> assertEquals("Debe existir al menos un administrador de sistema activo", respuesta.getBody().error()),
                () -> assertEquals("Los registros de usuario no se eliminan, solo se desactivan",
                        errorSql("ERROR: Los registros de usuario no se eliminan, solo se desactivan", "23000"))
        );
    }

    @Test
    void mensajeTrigger_casosLimite() {
        assertAll(
                () -> assertEquals("La operación viola una regla de datos", GlobalIdentityServiceExceptionHandler.mensajeTrigger(null)),
                () -> assertEquals("La operación viola una regla de datos", GlobalIdentityServiceExceptionHandler.mensajeTrigger(" ")),
                () -> assertEquals("Sin prefijo", GlobalIdentityServiceExceptionHandler.mensajeTrigger("Sin prefijo"))
        );
    }

    @Test
    void dataAccess_sinSqlConocido_devuelve500SinDetalles() {
        assertAll(
                () -> assertRespuesta(handler.handleDataAccess(new DataIntegrityViolationException("sin causa"), request),
                        HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO"),
                () -> assertRespuesta(handler.handleDataAccess(excepcionSql("timeout", "57014"), request),
                        HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO")
        );
    }

    @Test
    void seguridad_devuelve403Y401() {
        assertAll(
                () -> assertRespuesta(handler.handleAccessDenied(new AuthorizationDeniedException("Access Denied"), request),
                        HttpStatus.FORBIDDEN, "ACCESO_DENEGADO"),
                () -> assertRespuesta(handler.handleAuthentication(new BadCredentialsException("x"), request),
                        HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO")
        );
    }

    @Test
    void generica_devuelve500SinExponerElMensaje() {
        // Act
        ResponseEntity<RespuestaError> respuesta = handler.handleGeneric(new IllegalStateException("detalle interno"), request);

        // Assert
        assertAll(
                () -> assertRespuesta(respuesta, HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO"),
                () -> assertEquals(GlobalIdentityServiceExceptionHandler.MENSAJE_INTERNO, respuesta.getBody().error())
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
}
