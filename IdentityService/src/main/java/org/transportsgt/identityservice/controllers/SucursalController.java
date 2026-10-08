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
import org.transportsgt.identityservice.config.security.UsuarioAutenticado;
import org.transportsgt.identityservice.dto.request.ActualizarSucursalRequest;
import org.transportsgt.identityservice.dto.request.CrearSucursalRequest;
import org.transportsgt.identityservice.dto.response.PaginaResponse;
import org.transportsgt.identityservice.dto.response.SucursalCreadaResponse;
import org.transportsgt.identityservice.dto.response.SucursalResponse;
import org.transportsgt.identityservice.dto.response.UsuarioResponse;
import org.transportsgt.identityservice.exception.RespuestaError;
import org.transportsgt.identityservice.services.SucursalService;
import org.transportsgt.identityservice.utils.IpCliente;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/sucursales")
@RequiredArgsConstructor
@Tag(name = "Sucursales", description = "Administración de sucursales (solo ADMIN_SISTEMA las modifica)")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Sin token o token inválido",
                content = @Content(schema = @Schema(implementation = RespuestaError.class),
                        examples = @ExampleObject(value = EjemplosApi.ERROR_401)))
})
public class SucursalController {

    private static final String SOLO_ADMIN_SISTEMA = "hasRole('ADMIN_SISTEMA')";

    private final SucursalService sucursalService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SOLO_ADMIN_SISTEMA)
    @Operation(summary = "Crear sucursal con su primer administrador",
            description = "En una sola transacción crea la sucursal y su ADMIN_SUCURSAL, que recibe un enlace de "
                    + "activación (48 h). Publica SucursalCreada, UsuarioCreado y EnlaceContrasenaEmitido(ACTIVACION).",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    examples = @ExampleObject(value = """
                            {"sucursal":{"nombre":"Quetzaltenango Centro","departamento":"Quetzaltenango",
                            "direccion":"4a. Calle 12-35, Zona 1","telefono":"77651234","latitud":14.8345,"longitud":-91.5186},
                            "administrador":{"nombreCompleto":"María Fernanda Pérez","correo":"admin.xela@transportes.gt"}}"""))))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Sucursal y administrador creados"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_400))),
            @ApiResponse(responseCode = "403", description = "No es ADMIN_SISTEMA",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_403))),
            @ApiResponse(responseCode = "409", description = "Nombre de sucursal o correo ya registrado",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_409_SUCURSAL)))
    })
    public SucursalCreadaResponse crear(@Valid @RequestBody CrearSucursalRequest request,
                                        @AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {
        return sucursalService.crear(request, UsuarioAutenticado.de(jwt), IpCliente.de(http));
    }

    @GetMapping
    @Operation(summary = "Listar sucursales", description = "Paginado. Filtros opcionales: activa y nombre (contiene).")
    @ApiResponse(responseCode = "200", description = "Página de sucursales")
    public PaginaResponse<SucursalResponse> listar(
            @Parameter(description = "true = solo activas, false = solo inactivas") @RequestParam(required = false) Boolean activa,
            @Parameter(description = "Parte del nombre", example = "Xela") @RequestParam(required = false) String nombre,
            @ParameterObject @PageableDefault(size = 20, sort = "nombre", direction = Sort.Direction.ASC) Pageable pageable) {
        return sucursalService.listar(activa, nombre, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener una sucursal")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sucursal",
                    content = @Content(schema = @Schema(implementation = SucursalResponse.class),
                            examples = @ExampleObject(value = EjemplosApi.SUCURSAL))),
            @ApiResponse(responseCode = "404", description = "No existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_404_SUCURSAL)))
    })
    public SucursalResponse obtener(@PathVariable UUID id) {
        return sucursalService.obtener(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SOLO_ADMIN_SISTEMA)
    @Operation(summary = "Actualizar una sucursal", description = "Publica SucursalActualizada. "
            + "Si se envía version y ya no es la actual, responde 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sucursal actualizada",
                    content = @Content(schema = @Schema(implementation = SucursalResponse.class),
                            examples = @ExampleObject(value = EjemplosApi.SUCURSAL))),
            @ApiResponse(responseCode = "403", description = "No es ADMIN_SISTEMA",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_403))),
            @ApiResponse(responseCode = "404", description = "No existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_404_SUCURSAL))),
            @ApiResponse(responseCode = "409", description = "Nombre duplicado o versión desactualizada",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_409_SUCURSAL)))
    })
    public SucursalResponse actualizar(@PathVariable UUID id, @Valid @RequestBody ActualizarSucursalRequest request) {
        return sucursalService.actualizar(id, request);
    }

    @PatchMapping("/{id}/activar")
    @PreAuthorize(SOLO_ADMIN_SISTEMA)
    @Operation(summary = "Activar una sucursal", description = "Publica SucursalActualizada si estaba inactiva.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sucursal activa"),
            @ApiResponse(responseCode = "403", description = "No es ADMIN_SISTEMA",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_403))),
            @ApiResponse(responseCode = "404", description = "No existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_404_SUCURSAL)))
    })
    public SucursalResponse activar(@PathVariable UUID id) {
        return sucursalService.cambiarEstado(id, true);
    }

    @PatchMapping("/{id}/desactivar")
    @PreAuthorize(SOLO_ADMIN_SISTEMA)
    @Operation(summary = "Desactivar una sucursal", description = "Las sucursales no se eliminan. "
            + "Publica SucursalActualizada (activa=false) si estaba activa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sucursal inactiva"),
            @ApiResponse(responseCode = "403", description = "No es ADMIN_SISTEMA",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_403))),
            @ApiResponse(responseCode = "404", description = "No existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_404_SUCURSAL)))
    })
    public SucursalResponse desactivar(@PathVariable UUID id) {
        return sucursalService.cambiarEstado(id, false);
    }

    @GetMapping("/{id}/administradores")
    @PreAuthorize(SOLO_ADMIN_SISTEMA)
    @Operation(summary = "Administradores de una sucursal", description = "Usuarios ADMIN_SUCURSAL (activos e inactivos).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de administradores"),
            @ApiResponse(responseCode = "403", description = "No es ADMIN_SISTEMA",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_403))),
            @ApiResponse(responseCode = "404", description = "No existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_404_SUCURSAL)))
    })
    public List<UsuarioResponse> administradores(@PathVariable UUID id) {
        return sucursalService.listarAdministradores(id);
    }
}
