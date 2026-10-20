package org.transportsgt.tripservice.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.transportsgt.tripservice.config.security.UsuarioAutenticado;
import org.transportsgt.tripservice.dto.request.Requests;
import org.transportsgt.tripservice.dto.response.Responses;
import org.transportsgt.tripservice.exception.RespuestaError;
import org.transportsgt.tripservice.services.RutaService;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/rutas")
@RequiredArgsConstructor
@Tag(name = "Rutas", description = "Rutas de sucursal a sucursal")
public class RutaController {

    private final RutaService rutaService;

    @GetMapping
    @Operation(summary = "Listado de rutas", description = "Filtro opcional por sucursal de origen y destino.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado",
                    content = @Content(schema = @Schema(implementation = Responses.RutaResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sin token",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public List<Responses.RutaResponse> listar(
            @Parameter(description = "Filtrar por sucursal de origen")
            @RequestParam(required = false) UUID idSucursalOrigen,
            @Parameter(description = "Filtrar por sucursal de destino")
            @RequestParam(required = false) UUID idSucursalDestino) {
        return rutaService.listar(idSucursalOrigen, idSucursalDestino);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de una ruta")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ruta",
                    content = @Content(schema = @Schema(implementation = Responses.RutaResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public Responses.RutaResponse detalle(@PathVariable UUID id) {
        return rutaService.detalle(id);
    }

    @PostMapping
    @Operation(summary = "Crear ruta (admin de sucursal de origen)",
            description = "Publica RutaCreada. El origen debe ser la sucursal del administrador.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Ruta creada",
                    content = @Content(schema = @Schema(implementation = Responses.RutaResponse.class))),
            @ApiResponse(responseCode = "403", description = "No es admin de la sucursal de origen",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "409", description = "Ya existe una ruta entre esas sucursales",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public ResponseEntity<Responses.RutaResponse> crear(
            @Valid @RequestBody Requests.RutaRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Responses.RutaResponse resp = rutaService.crear(request, UsuarioAutenticado.de(jwt));
        return ResponseEntity.created(URI.create("/rutas/" + resp.id())).body(resp);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar ruta (admin de sucursal de origen)", description = "Publica RutaActualizada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ruta actualizada",
                    content = @Content(schema = @Schema(implementation = Responses.RutaResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public Responses.RutaResponse actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody Requests.RutaRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return rutaService.actualizar(id, request, UsuarioAutenticado.de(jwt));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar ruta (admin de sucursal de origen)",
            description = "Solo si no tiene viajes asociados; si no, 409 RUTA_CON_VIAJES. Publica RutaEliminada.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Eliminada"),
            @ApiResponse(responseCode = "409", description = "La ruta tiene viajes asociados",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                                    value = "{\"codigo\":\"RUTA_CON_VIAJES\"}")))
    })
    public ResponseEntity<Void> eliminar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        rutaService.eliminar(id, UsuarioAutenticado.de(jwt));
        return ResponseEntity.noContent().build();
    }
}
