package org.transportsgt.identityservice.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.identityservice.config.security.UsuarioAutenticado;
import org.transportsgt.identityservice.dto.request.ActualizarPerfilRequest;
import org.transportsgt.identityservice.dto.request.ActualizarUsuarioRequest;
import org.transportsgt.identityservice.dto.request.CrearUsuarioRequest;
import org.transportsgt.identityservice.dto.response.PaginaResponse;
import org.transportsgt.identityservice.dto.response.UsuarioResponse;
import org.transportsgt.identityservice.exception.RespuestaError;
import org.transportsgt.identityservice.services.UsuarioService;
import org.transportsgt.identityservice.utils.IpCliente;

import java.util.UUID;

@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
@Tag(name = "Usuarios", description = "Administración de usuarios. Los usuarios no se eliminan, solo se desactivan.")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Sin token o token inválido",
                content = @Content(schema = @Schema(implementation = RespuestaError.class),
                        examples = @ExampleObject(value = EjemplosApi.ERROR_401)))
})
public class UsuarioController {

    private static final String ADMINISTRADORES = "hasAnyRole('ADMIN_SISTEMA','ADMIN_SUCURSAL')";

    private final UsuarioService usuarioService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(ADMINISTRADORES)
    @Operation(summary = "Crear usuario de personal",
            description = "ADMIN_SISTEMA crea ADMIN_SISTEMA o ADMIN_SUCURSAL; ADMIN_SUCURSAL crea CAJERO o CHOFER en su "
                    + "propia sucursal. Cualquier otra combinación responde 403. El usuario recibe un enlace de "
                    + "activación (48 h) para definir su contraseña. Publica UsuarioCreado y EnlaceContrasenaEmitido.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    examples = @ExampleObject(value = """
                            {"nombreCompleto":"Ana López","correo":"cajero.xela@transportes.gt","rol":"CAJERO"}"""))))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuario creado",
                    content = @Content(schema = @Schema(implementation = UsuarioResponse.class),
                            examples = @ExampleObject(value = EjemplosApi.USUARIO))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_400))),
            @ApiResponse(responseCode = "403", description = "Combinación de roles no permitida u otra sucursal",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_403))),
            @ApiResponse(responseCode = "404", description = "La sucursal no existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_404_SUCURSAL))),
            @ApiResponse(responseCode = "409", description = "Correo ya registrado",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_409_CORREO))),
            @ApiResponse(responseCode = "422", description = "Sucursal inactiva o no corresponde al rol",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public UsuarioResponse crear(@Valid @RequestBody CrearUsuarioRequest request,
                                 @AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {
        return usuarioService.crear(request, UsuarioAutenticado.de(jwt), IpCliente.de(http));
    }

    @GetMapping
    @PreAuthorize(ADMINISTRADORES)
    @Operation(summary = "Listar usuarios", description = "Paginado con filtros opcionales. "
            + "ADMIN_SUCURSAL solo ve los usuarios de su sucursal.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de usuarios"),
            @ApiResponse(responseCode = "403", description = "Rol sin acceso o sucursal ajena",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_403)))
    })
    public PaginaResponse<UsuarioResponse> listar(
            @RequestParam(required = false) Rol rol,
            @RequestParam(required = false) UUID idSucursal,
            @RequestParam(required = false) Boolean activo,
            @Parameter(description = "Busca en nombre y correo", example = "lopez")
            @RequestParam(required = false) String texto,
            @ParameterObject @PageableDefault(size = 20, sort = "nombreCompleto", direction = Sort.Direction.ASC)
            Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        return usuarioService.listar(rol, idSucursal, activo, texto, pageable, UsuarioAutenticado.de(jwt));
    }

    @PutMapping("/yo")
    @Operation(summary = "Editar mi nombre", description = "Cualquier usuario autenticado. Publica UsuarioActualizado.")
    @ApiResponse(responseCode = "200", description = "Perfil actualizado",
            content = @Content(schema = @Schema(implementation = UsuarioResponse.class),
                    examples = @ExampleObject(value = EjemplosApi.USUARIO)))
    public UsuarioResponse actualizarPerfil(@Valid @RequestBody ActualizarPerfilRequest request,
                                            @AuthenticationPrincipal Jwt jwt) {
        return usuarioService.actualizarPerfil(request, UsuarioAutenticado.de(jwt));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un usuario", description = "ADMIN_SISTEMA ve a cualquiera; ADMIN_SUCURSAL a los de "
            + "su sucursal; los demás solo a sí mismos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario",
                    content = @Content(schema = @Schema(implementation = UsuarioResponse.class),
                            examples = @ExampleObject(value = EjemplosApi.USUARIO))),
            @ApiResponse(responseCode = "403", description = "Sin acceso a ese usuario",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_403))),
            @ApiResponse(responseCode = "404", description = "No existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_404_USUARIO)))
    })
    public UsuarioResponse obtener(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return usuarioService.obtener(id, UsuarioAutenticado.de(jwt));
    }

    @PutMapping("/{id}")
    @PreAuthorize(ADMINISTRADORES)
    @Operation(summary = "Editar nombre y correo", description = "ADMIN_SUCURSAL solo edita CAJERO y CHOFER de su "
            + "sucursal. Publica UsuarioActualizado. Si se envía version y ya no es la actual, responde 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario actualizado",
                    content = @Content(schema = @Schema(implementation = UsuarioResponse.class),
                            examples = @ExampleObject(value = EjemplosApi.USUARIO))),
            @ApiResponse(responseCode = "403", description = "Sin acceso a ese usuario",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_403))),
            @ApiResponse(responseCode = "404", description = "No existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_404_USUARIO))),
            @ApiResponse(responseCode = "409", description = "Correo duplicado o versión desactualizada",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_409_CORREO)))
    })
    public UsuarioResponse actualizar(@PathVariable UUID id, @Valid @RequestBody ActualizarUsuarioRequest request,
                                      @AuthenticationPrincipal Jwt jwt) {
        return usuarioService.actualizar(id, request, UsuarioAutenticado.de(jwt));
    }

    @PatchMapping("/{id}/activar")
    @PreAuthorize(ADMINISTRADORES)
    @Operation(summary = "Activar un usuario", description = "Publica UsuarioActualizado si estaba inactivo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario activo"),
            @ApiResponse(responseCode = "403", description = "Sin acceso a ese usuario",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_403))),
            @ApiResponse(responseCode = "404", description = "No existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_404_USUARIO)))
    })
    public UsuarioResponse activar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return usuarioService.cambiarEstado(id, true, UsuarioAutenticado.de(jwt));
    }

    @PatchMapping("/{id}/desactivar")
    @PreAuthorize(ADMINISTRADORES)
    @Operation(summary = "Desactivar un usuario", description = "No existe DELETE. Siempre debe quedar un "
            + "ADMIN_SISTEMA activo y un ADMIN_SUCURSAL activo por sucursal. Publica UsuarioActualizado (activo=false).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario inactivo"),
            @ApiResponse(responseCode = "403", description = "Sin acceso a ese usuario",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_403))),
            @ApiResponse(responseCode = "404", description = "No existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_404_USUARIO))),
            @ApiResponse(responseCode = "422", description = "Es el último administrador activo",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_422_ULTIMO_ADMIN)))
    })
    public UsuarioResponse desactivar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return usuarioService.cambiarEstado(id, false, UsuarioAutenticado.de(jwt));
    }

    @PostMapping("/{id}/reenviar-activacion")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize(ADMINISTRADORES)
    @Operation(summary = "Reenviar enlace de activación",
            description = "Invalida el enlace anterior y emite uno nuevo (48 h). Solo para usuarios activos que aún "
                    + "no definieron su contraseña. Publica EnlaceContrasenaEmitido(ACTIVACION).")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Enlace emitido"),
            @ApiResponse(responseCode = "403", description = "Sin acceso a ese usuario",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_403))),
            @ApiResponse(responseCode = "404", description = "No existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_404_USUARIO))),
            @ApiResponse(responseCode = "422", description = "El usuario ya activó su cuenta o está inactivo",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public void reenviarActivacion(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {
        usuarioService.reenviarActivacion(id, UsuarioAutenticado.de(jwt), IpCliente.de(http));
    }
}
