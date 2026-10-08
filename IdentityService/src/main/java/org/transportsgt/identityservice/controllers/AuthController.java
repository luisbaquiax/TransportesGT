package org.transportsgt.identityservice.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.transportsgt.identityservice.config.security.UsuarioAutenticado;
import org.transportsgt.identityservice.dto.request.CambiarContrasenaRequest;
import org.transportsgt.identityservice.dto.request.LoginRequest;
import org.transportsgt.identityservice.dto.request.OlvideContrasenaRequest;
import org.transportsgt.identityservice.dto.request.RegistroRequest;
import org.transportsgt.identityservice.dto.request.RestablecerContrasenaRequest;
import org.transportsgt.identityservice.dto.response.EnlaceContrasenaResponse;
import org.transportsgt.identityservice.dto.response.LoginResponse;
import org.transportsgt.identityservice.dto.response.MensajeResponse;
import org.transportsgt.identityservice.dto.response.UsuarioResponse;
import org.transportsgt.identityservice.exception.RespuestaError;
import org.transportsgt.identityservice.services.AuthService;
import org.transportsgt.identityservice.utils.IpCliente;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Inicio de sesión, registro de clientes y gestión de contraseñas")
public class AuthController {

    static final String MENSAJE_OLVIDE =
            "Si el correo está registrado, recibirá un enlace para restablecer su contraseña";

    private final AuthService authService;

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Iniciar sesión", description = "Devuelve el JWT (30–60 min) con los claims sub, correo, "
            + "rol, idSucursal y nombre.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sesión iniciada",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class),
                            examples = @ExampleObject(value = """
                                    {"token":"eyJhbGciOiJIUzI1NiJ9...","expiraEn":"2026-10-07T11:15:30Z",
                                    "usuario":{"id":"0b5c8a6e-2f6d-4c1a-9e3b-7d8f9a0b1c2d","correo":"admin@transportes.gt",
                                    "nombreCompleto":"Administrador del Sistema","rol":"ADMIN_SISTEMA","idSucursal":null}}"""))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "401", description = "Correo o contraseña incorrectos",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = """
                                    {"timestamp":"2026-10-07T10:15:30","status":401,"error":"Correo o contraseña incorrectos","codigo":"CREDENCIALES_INVALIDAS","ruta":"/v1/identity/auth/login"}"""))),
            @ApiResponse(responseCode = "403", description = "Usuario desactivado",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = """
                                    {"timestamp":"2026-10-07T10:15:30","status":403,"error":"El usuario está desactivado","codigo":"USUARIO_INACTIVO","ruta":"/v1/identity/auth/login"}""")))
    })
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements
    @Operation(summary = "Registrarse como cliente", description = "Crea un usuario con rol CLIENTE. "
            + "Publica UsuarioCreado (Clientes y Cartera crea su perfil y cartera).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cliente registrado",
                    content = @Content(schema = @Schema(implementation = UsuarioResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o contraseña débil",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = """
                                    {"timestamp":"2026-10-07T10:15:30","status":400,"error":"contrasena: debe tener entre 8 y 72 caracteres e incluir al menos una mayúscula, una minúscula y un número","codigo":"VALIDACION_FALLIDA","ruta":"/v1/identity/auth/registro"}"""))),
            @ApiResponse(responseCode = "409", description = "Correo ya registrado",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_409_CORREO)))
    })
    public UsuarioResponse registrar(@Valid @RequestBody RegistroRequest request) {
        return authService.registrar(request);
    }

    @GetMapping("/yo")
    @Operation(summary = "Mis datos", description = "Datos del usuario autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario autenticado",
                    content = @Content(schema = @Schema(implementation = UsuarioResponse.class),
                            examples = @ExampleObject(value = EjemplosApi.USUARIO))),
            @ApiResponse(responseCode = "401", description = "Sin token o token inválido",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_401)))
    })
    public UsuarioResponse yo(@AuthenticationPrincipal Jwt jwt) {
        return authService.miPerfil(UsuarioAutenticado.de(jwt));
    }

    @PostMapping("/cambiar-contrasena")
    @Operation(summary = "Cambiar mi contraseña", description = "Exige la contraseña actual; la nueva debe cumplir "
            + "la política y ser distinta. Publica ContrasenaCambiada(CAMBIO).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contraseña cambiada"),
            @ApiResponse(responseCode = "400", description = "La nueva contraseña no cumple la política",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "401", description = "Sin token o token inválido",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_401))),
            @ApiResponse(responseCode = "422", description = "Contraseña actual incorrecta o nueva igual a la actual",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = """
                                    {"timestamp":"2026-10-07T10:15:30","status":422,"error":"La contraseña actual no es correcta","codigo":"CONTRASENA_ACTUAL_INCORRECTA","ruta":"/v1/identity/auth/cambiar-contrasena"}""")))
    })
    public MensajeResponse cambiarContrasena(@Valid @RequestBody CambiarContrasenaRequest request,
                                             @AuthenticationPrincipal Jwt jwt) {
        authService.cambiarContrasena(request, UsuarioAutenticado.de(jwt));
        return new MensajeResponse("Contraseña actualizada");
    }

    @PostMapping("/olvide-contrasena")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @SecurityRequirements
    @Operation(summary = "Olvidé mi contraseña", description = "Siempre responde 202 con el mismo mensaje (no revela "
            + "si el correo existe). Máximo 3 solicitudes por hora por usuario. Publica "
            + "EnlaceContrasenaEmitido(RESTABLECIMIENTO), vigente 30 min.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Solicitud recibida",
                    content = @Content(schema = @Schema(implementation = MensajeResponse.class),
                            examples = @ExampleObject(value = "{\"mensaje\":\"" + MENSAJE_OLVIDE + "\"}"))),
            @ApiResponse(responseCode = "400", description = "Correo con formato inválido",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public MensajeResponse olvideContrasena(@Valid @RequestBody OlvideContrasenaRequest request,
                                            HttpServletRequest http) {
        authService.olvideContrasena(request, IpCliente.de(http));
        return new MensajeResponse(MENSAJE_OLVIDE);
    }

    @GetMapping("/enlace-contrasena/validar")
    @SecurityRequirements
    @Operation(summary = "Validar un enlace de contraseña", description = "Para que el frontend muestre el formulario "
            + "solo si el enlace sigue vigente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Enlace vigente",
                    content = @Content(schema = @Schema(implementation = EnlaceContrasenaResponse.class),
                            examples = @ExampleObject(value = "{\"proposito\":\"RESTABLECIMIENTO\",\"correoEnmascarado\":\"c***a@transportes.gt\"}"))),
            @ApiResponse(responseCode = "410", description = "Enlace inexistente, usado, reemplazado o vencido",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_410_TOKEN)))
    })
    public EnlaceContrasenaResponse validarEnlace(
            @Parameter(description = "Token recibido en el correo") @RequestParam @NotBlank String token) {
        return authService.validarEnlace(token);
    }

    @PostMapping("/restablecer-contrasena")
    @SecurityRequirements
    @Operation(summary = "Definir una nueva contraseña con un enlace",
            description = "Sirve para RESTABLECIMIENTO y ACTIVACION. En una sola transacción actualiza la contraseña, "
                    + "marca el enlace como usado, invalida los demás y publica ContrasenaCambiada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contraseña definida",
                    content = @Content(schema = @Schema(implementation = MensajeResponse.class))),
            @ApiResponse(responseCode = "400", description = "La contraseña no cumple la política",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "410", description = "Enlace inexistente, usado, reemplazado o vencido",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_410_TOKEN))),
            @ApiResponse(responseCode = "422", description = "La nueva contraseña es igual a la actual",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public MensajeResponse restablecerContrasena(@Valid @RequestBody RestablecerContrasenaRequest request) {
        authService.restablecerContrasena(request);
        return new MensajeResponse("Contraseña definida. Ya puede iniciar sesión");
    }
}
