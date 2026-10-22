package org.transportsgt.fleetservice.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;
import org.transportsgt.fleetservice.config.security.UsuarioAutenticado;
import org.transportsgt.fleetservice.dto.request.ActualizarChoferRequest;
import org.transportsgt.fleetservice.dto.request.CambiarActivoRequest;
import org.transportsgt.fleetservice.dto.request.CrearChoferRequest;
import org.transportsgt.fleetservice.dto.response.ChoferResponse;
import org.transportsgt.fleetservice.dto.response.PaginaResponse;
import org.transportsgt.fleetservice.exception.RespuestaError;
import org.transportsgt.fleetservice.services.ChoferService;
import org.transportsgt.fleetservice.services.FotoService;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/choferes")
@RequiredArgsConstructor
@Tag(name = "Choferes", description = "Administración de choferes (SIS consulta; SUC gestiona los de su sucursal)")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Sin token o token inválido",
                content = @Content(schema = @Schema(implementation = RespuestaError.class),
                        examples = @ExampleObject(value = EjemplosApi.ERROR_401))),
        @ApiResponse(responseCode = "403", description = "No tiene permisos",
                content = @Content(schema = @Schema(implementation = RespuestaError.class),
                        examples = @ExampleObject(value = EjemplosApi.ERROR_403)))
})
public class ChoferController {

    private static final String SOLO_ADMIN_SUCURSAL = "hasRole('ADMIN_SUCURSAL')";

    private final ChoferService choferService;

    @GetMapping
    @Operation(summary = "Listar choferes",
            description = "Paginado. Filtros opcionales: idSucursal, activo y texto (contiene en nombre, correo "
                    + "o número de licencia). ADMIN_SUCURSAL solo ve los de su sucursal.")
    @ApiResponse(responseCode = "200", description = "Página de choferes",
            content = @Content(schema = @Schema(implementation = PaginaResponse.class),
                    examples = @ExampleObject(value = EjemplosApi.PAGINA_CHOFERES)))
    public PaginaResponse<ChoferResponse> listar(
            @Parameter(description = "Filtrar por sucursal (SIS; SUC solo puede usar la suya)")
            @RequestParam(required = false) UUID idSucursal,
            @Parameter(description = "true = solo activos, false = solo inactivos")
            @RequestParam(required = false) Boolean activo,
            @Parameter(description = "Parte del nombre, correo o licencia", example = "Pérez")
            @RequestParam(required = false) String texto,
            @ParameterObject @PageableDefault(size = 20, sort = "nombreCompleto", direction = Sort.Direction.ASC)
            Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        return choferService.listar(idSucursal, activo, texto, pageable, UsuarioAutenticado.de(jwt));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un chofer")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chofer",
                    content = @Content(schema = @Schema(implementation = ChoferResponse.class),
                            examples = @ExampleObject(value = EjemplosApi.CHOFER))),
            @ApiResponse(responseCode = "404", description = "No existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_404_CHOFER)))
    })
    public ChoferResponse obtener(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return choferService.obtener(id, UsuarioAutenticado.de(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SOLO_ADMIN_SUCURSAL)
    @Operation(summary = "Registrar un chofer",
            description = "Crea el chofer en la sucursal del administrador. idUsuario enlaza con el usuario de "
                    + "Identidad (rol CHOFER) creado antes, para que pueda registrar su salida. "
                    + "Publica ChoferRegistrado.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    examples = @ExampleObject(value = """
                            {"nombreCompleto":"Juan Carlos Pérez","correo":"chofer.xela@transportes.gt",
                            "telefono":"5555-1234","numeroLicencia":"LIC-XEL-001","tipoLicencia":"B",
                            "fechaVencimientoLicencia":"2027-03-15","salarioBaseViaje":150.00,
                            "idUsuario":"1c9e4a52-0d2b-4f3e-8a77-6f1b2c3d4e5f"}"""))))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Chofer creado",
                    content = @Content(schema = @Schema(implementation = ChoferResponse.class),
                            examples = @ExampleObject(value = EjemplosApi.CHOFER))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_400))),
            @ApiResponse(responseCode = "409", description = "Licencia duplicada, usuario ya enlazado "
                    + "o sucursal no activa",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_409_LICENCIA)))
    })
    public ChoferResponse crear(@Valid @RequestBody CrearChoferRequest request, @AuthenticationPrincipal Jwt jwt) {
        return choferService.crear(request, UsuarioAutenticado.de(jwt));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SOLO_ADMIN_SUCURSAL)
    @Operation(summary = "Actualizar un chofer",
            description = "Publica ChoferActualizado (Notificaciones reprograma los avisos de licencia). "
                    + "Si se envía version y ya no es la actual, responde 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chofer actualizado",
                    content = @Content(schema = @Schema(implementation = ChoferResponse.class),
                            examples = @ExampleObject(value = EjemplosApi.CHOFER))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_400))),
            @ApiResponse(responseCode = "404", description = "No existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_404_CHOFER))),
            @ApiResponse(responseCode = "409", description = "Licencia duplicada, usuario ya enlazado "
                    + "o versión desactualizada",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_409_LICENCIA)))
    })
    public ChoferResponse actualizar(@PathVariable UUID id, @Valid @RequestBody ActualizarChoferRequest request,
                                     @AuthenticationPrincipal Jwt jwt) {
        return choferService.actualizar(id, request, UsuarioAutenticado.de(jwt));
    }

    @PatchMapping("/{id}/activo")
    @PreAuthorize(SOLO_ADMIN_SUCURSAL)
    @Operation(summary = "Activar o desactivar un chofer",
            description = "Los choferes no se eliminan. Publica ChoferActualizado solo si el estado cambia.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado activo actualizado",
                    content = @Content(schema = @Schema(implementation = ChoferResponse.class),
                            examples = @ExampleObject(value = EjemplosApi.CHOFER))),
            @ApiResponse(responseCode = "404", description = "No existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_404_CHOFER))),
            @ApiResponse(responseCode = "409", description = "Versión desactualizada",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_409_LICENCIA)))
    })
    public ChoferResponse cambiarActivo(@PathVariable UUID id, @Valid @RequestBody CambiarActivoRequest request,
                                        @AuthenticationPrincipal Jwt jwt) {
        return choferService.cambiarActivo(id, request, UsuarioAutenticado.de(jwt));
    }

    @GetMapping("/{id}/foto")
    @Operation(summary = "Obtener la foto de un chofer",
            description = "Foto privada: misma autorización que GET /{id} (SIS ve todas; SUC solo las de su sucursal). "
                    + "La fotoUrl de la respuesta del chofer apunta aquí con ?v={version} para invalidar la caché.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Imagen (JPEG, PNG o WebP)"),
            @ApiResponse(responseCode = "403", description = "El chofer es de otra sucursal",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "404", description = "No existe o no tiene foto",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public ResponseEntity<Resource> leerFoto(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        FotoService.FotoLeida foto = choferService.leerFoto(id, UsuarioAutenticado.de(jwt));
        return ResponseEntity.ok()
                .contentType(foto.tipo())
                // private: solo la caché del navegador, nunca una compartida (proxy/CDN)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .body(foto.contenido());
    }

    @PostMapping("/{id}/foto")
    @PreAuthorize(SOLO_ADMIN_SUCURSAL)
    @Operation(summary = "Subir la foto de un chofer",
            description = "multipart/form-data con el campo 'archivo' (JPEG, PNG o WebP, máximo 5 MB). "
                    + "Guarda la imagen (privada: se lee con GET /{id}/foto) y deja su referencia en foto_url.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    examples = @ExampleObject(value = "archivo=@chofer.jpg"))))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Foto guardada",
                    content = @Content(schema = @Schema(implementation = ChoferResponse.class),
                            examples = @ExampleObject(value = EjemplosApi.CHOFER))),
            @ApiResponse(responseCode = "400", description = "Formato o tamaño no permitido",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_400_FOTO))),
            @ApiResponse(responseCode = "404", description = "No existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_404_CHOFER)))
    })
    public ChoferResponse guardarFoto(@PathVariable UUID id,
                                      @RequestParam("archivo") MultipartFile archivo,
                                      @AuthenticationPrincipal Jwt jwt) {
        return choferService.guardarFoto(id, archivo, UsuarioAutenticado.de(jwt));
    }
}
