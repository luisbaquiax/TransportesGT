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
import org.springframework.web.multipart.MultipartFile;
import org.transportsgt.eventos.payload.EstadoOperativo;
import org.transportsgt.fleetservice.config.security.UsuarioAutenticado;
import org.transportsgt.fleetservice.dto.request.ActualizarBusRequest;
import org.transportsgt.fleetservice.dto.request.CambiarActivoRequest;
import org.transportsgt.fleetservice.dto.request.CambiarEstadoOperativoRequest;
import org.transportsgt.fleetservice.dto.request.CrearBusRequest;
import org.transportsgt.fleetservice.dto.response.BusResponse;
import org.transportsgt.fleetservice.dto.response.PaginaResponse;
import org.transportsgt.fleetservice.exception.RespuestaError;
import org.transportsgt.fleetservice.services.BusService;

import java.util.UUID;

@RestController
@RequestMapping("/buses")
@RequiredArgsConstructor
@Tag(name = "Buses", description = "Administración de buses (SIS consulta; SUC gestiona los de su sucursal)")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Sin token o token inválido",
                content = @Content(schema = @Schema(implementation = RespuestaError.class),
                        examples = @ExampleObject(value = EjemplosApi.ERROR_401))),
        @ApiResponse(responseCode = "403", description = "No tiene permisos",
                content = @Content(schema = @Schema(implementation = RespuestaError.class),
                        examples = @ExampleObject(value = EjemplosApi.ERROR_403)))
})
public class BusController {

    private static final String SOLO_ADMIN_SUCURSAL = "hasRole('ADMIN_SUCURSAL')";

    private final BusService busService;

    @GetMapping
    @Operation(summary = "Listar buses",
            description = "Paginado. Filtros opcionales: idSucursal, estadoOperativo, activo y texto (contiene en "
                    + "placa, marca o modelo). ADMIN_SUCURSAL solo ve los de su sucursal.")
    @ApiResponse(responseCode = "200", description = "Página de buses",
            content = @Content(schema = @Schema(implementation = PaginaResponse.class),
                    examples = @ExampleObject(value = EjemplosApi.PAGINA_BUSES)))
    public PaginaResponse<BusResponse> listar(
            @Parameter(description = "Filtrar por sucursal (SIS; SUC solo puede usar la suya)")
            @RequestParam(required = false) UUID idSucursal,
            @Parameter(description = "DISPONIBLE, EN_TRANSITO, EN_TALLER o FUERA_DE_SERVICIO")
            @RequestParam(required = false) EstadoOperativo estadoOperativo,
            @Parameter(description = "true = solo activos, false = solo inactivos")
            @RequestParam(required = false) Boolean activo,
            @Parameter(description = "Parte de la placa, marca o modelo", example = "Hiace")
            @RequestParam(required = false) String texto,
            @ParameterObject @PageableDefault(size = 20, sort = "creadoEn", direction = Sort.Direction.DESC)
            Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        return busService.listar(idSucursal, estadoOperativo, activo, texto, pageable, UsuarioAutenticado.de(jwt));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un bus")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bus",
                    content = @Content(schema = @Schema(implementation = BusResponse.class),
                            examples = @ExampleObject(value = EjemplosApi.BUS))),
            @ApiResponse(responseCode = "404", description = "No existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_404_BUS)))
    })
    public BusResponse obtener(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return busService.obtener(id, UsuarioAutenticado.de(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SOLO_ADMIN_SUCURSAL)
    @Operation(summary = "Registrar un bus",
            description = "Crea el bus en la sucursal del administrador con estado DISPONIBLE y kilometraje 0 "
                    + "si no se envía. Publica BusRegistrado.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    examples = @ExampleObject(value = """
                            {"placa":"P123XYZ","marca":"Toyota","modelo":"Hiace","anioFabricacion":2019,
                            "capacidadPasajeros":45,"kilometrajeActual":0}"""))))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Bus creado",
                    content = @Content(schema = @Schema(implementation = BusResponse.class),
                            examples = @ExampleObject(value = EjemplosApi.BUS))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_400))),
            @ApiResponse(responseCode = "409", description = "La placa ya existe o la sucursal no está activa",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_409_PLACA)))
    })
    public BusResponse crear(@Valid @RequestBody CrearBusRequest request, @AuthenticationPrincipal Jwt jwt) {
        return busService.crear(request, UsuarioAutenticado.de(jwt));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SOLO_ADMIN_SUCURSAL)
    @Operation(summary = "Actualizar un bus",
            description = "Placa, marca, modelo, año y capacidad. Publica BusActualizado. "
                    + "Si se envía version y ya no es la actual, responde 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bus actualizado",
                    content = @Content(schema = @Schema(implementation = BusResponse.class),
                            examples = @ExampleObject(value = EjemplosApi.BUS))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_400))),
            @ApiResponse(responseCode = "404", description = "No existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_404_BUS))),
            @ApiResponse(responseCode = "409", description = "Placa duplicada o versión desactualizada",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_409_PLACA)))
    })
    public BusResponse actualizar(@PathVariable UUID id, @Valid @RequestBody ActualizarBusRequest request,
                                  @AuthenticationPrincipal Jwt jwt) {
        return busService.actualizar(id, request, UsuarioAutenticado.de(jwt));
    }

    @PatchMapping("/{id}/activo")
    @PreAuthorize(SOLO_ADMIN_SUCURSAL)
    @Operation(summary = "Activar o desactivar un bus",
            description = "Para desactivar consulta a Viajes (GET /viajes/activos): si hay viajes programados o "
                    + "en tránsito → 409 BUS_CON_VIAJES; si Viajes no responde → 503 (falla cerrada). "
                    + "Publica BusActualizado solo si el estado cambia.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado activo actualizado",
                    content = @Content(schema = @Schema(implementation = BusResponse.class),
                            examples = @ExampleObject(value = EjemplosApi.BUS))),
            @ApiResponse(responseCode = "404", description = "No existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_404_BUS))),
            @ApiResponse(responseCode = "409", description = "El bus tiene viajes programados o en tránsito",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_409_BUS_CON_VIAJES))),
            @ApiResponse(responseCode = "503", description = "Viajes no está disponible",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_503)))
    })
    public BusResponse cambiarActivo(@PathVariable UUID id, @Valid @RequestBody CambiarActivoRequest request,
                                     @AuthenticationPrincipal Jwt jwt) {
        return busService.cambiarActivo(id, request, UsuarioAutenticado.de(jwt));
    }

    @PatchMapping("/{id}/estado-operativo")
    @PreAuthorize(SOLO_ADMIN_SUCURSAL)
    @Operation(summary = "Cambiar el estado operativo de un bus",
            description = "DISPONIBLE, EN_TALLER o FUERA_DE_SERVICIO. EN_TRANSITO solo cambia al registrar la "
                    + "salida y la llegada de un viaje (SalidaRegistrada / LlegadaRegistrada). "
                    + "Publica BusActualizado solo si el estado cambia.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado operativo actualizado",
                    content = @Content(schema = @Schema(implementation = BusResponse.class),
                            examples = @ExampleObject(value = EjemplosApi.BUS))),
            @ApiResponse(responseCode = "404", description = "No existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_404_BUS))),
            @ApiResponse(responseCode = "409", description = "Transición inválida (EN_TRANSITO o versión vieja)",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_409_TRANSICION)))
    })
    public BusResponse cambiarEstadoOperativo(@PathVariable UUID id,
                                              @Valid @RequestBody CambiarEstadoOperativoRequest request,
                                              @AuthenticationPrincipal Jwt jwt) {
        return busService.cambiarEstadoOperativo(id, request, UsuarioAutenticado.de(jwt));
    }

    @PostMapping("/{id}/foto")
    @PreAuthorize(SOLO_ADMIN_SUCURSAL)
    @Operation(summary = "Subir la foto de un bus",
            description = "multipart/form-data con el campo 'archivo' (JPEG, PNG o WebP, máximo 5 MB). "
                    + "Guarda la imagen en disco y deja la URL en foto_url.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE,
                    examples = @ExampleObject(value = "archivo=@bus.jpg"))))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Foto guardada",
                    content = @Content(schema = @Schema(implementation = BusResponse.class),
                            examples = @ExampleObject(value = EjemplosApi.BUS))),
            @ApiResponse(responseCode = "400", description = "Formato o tamaño no permitido",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_400_FOTO))),
            @ApiResponse(responseCode = "404", description = "No existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_404_BUS)))
    })
    public BusResponse guardarFoto(@PathVariable UUID id,
                                   @RequestParam("archivo") MultipartFile archivo,
                                   @AuthenticationPrincipal Jwt jwt) {
        return busService.guardarFoto(id, archivo, UsuarioAutenticado.de(jwt));
    }
}
