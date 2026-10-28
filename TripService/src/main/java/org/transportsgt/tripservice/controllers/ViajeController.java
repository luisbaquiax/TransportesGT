package org.transportsgt.tripservice.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.transportsgt.eventos.payload.viajes.EstadoViaje;
import org.transportsgt.eventos.payload.viajes.TipoViaje;
import org.transportsgt.tripservice.config.security.authentication.ConvertidorUsuarioAutenticado;
import org.transportsgt.tripservice.dto.request.Requests;
import org.transportsgt.tripservice.dto.response.PaginaResponse;
import org.transportsgt.tripservice.dto.response.Responses;
import org.transportsgt.tripservice.exception.RespuestaError;
import org.transportsgt.tripservice.services.consulta.ConsultaViajesService;
import org.transportsgt.tripservice.services.viaje.ProgramacionViajesService;
import org.transportsgt.tripservice.services.viaje.RegistroViajeService;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/viajes")
@RequiredArgsConstructor
@Tag(name = "Viajes", description = "Viajes regulares y de alquiler privado")
public class ViajeController {

    private final ConsultaViajesService consultaViajes;
    private final ProgramacionViajesService programacionViajes;
    private final RegistroViajeService registroViaje;
    private final ConvertidorUsuarioAutenticado convertidor;

    @GetMapping
    @Operation(summary = "Listado de viajes",
            description = "Paginado y filtrado en la base de datos. SIS ve todos; SUC y CAJ ven los de su sucursal; "
                    + "CHO solo los suyos. Ordenado por hora de salida. desde y hasta son inclusivos. "
                    + "sinAsignar=true lista los alquileres pendientes de bus y chofer.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de viajes",
                    content = @Content(schema = @Schema(implementation = PaginaResponse.class))),
            @ApiResponse(responseCode = "400", description = "Paginación o filtros inválidos",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "401", description = "Sin token",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public PaginaResponse<Responses.ViajeResponse> listar(
            @RequestParam(required = false) TipoViaje tipoViaje,
            @RequestParam(required = false) EstadoViaje estado,
            @RequestParam(required = false) UUID idRuta,
            @RequestParam(required = false) UUID idSucursal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta,
            @RequestParam(required = false) Boolean sinAsignar,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano,
            @AuthenticationPrincipal Jwt jwt) {
        return consultaViajes.listar(
                new Requests.FiltroViajes(tipoViaje, estado, idRuta, idSucursal, desde, hasta, sinAsignar),
                pagina, tamano, convertidor.desde(jwt));
    }

    @GetMapping("/resumen")
    @Operation(summary = "Resumen público de varios viajes",
            description = "Para el historial de compras: origen, destino, horarios, precio y placa, sin datos del chofer "
                    + "ni registros de salida o llegada. Cualquier usuario autenticado. Los ids inexistentes o de "
                    + "viajes eliminados se omiten. Máximo " + ConsultaViajesService.MAX_IDS_RESUMEN + " ids, separados por coma.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Viajes encontrados, por hora de salida",
                    content = @Content(schema = @Schema(implementation = Responses.ViajeResumenResponse.class))),
            @ApiResponse(responseCode = "400", description = "Sin ids, más del máximo o un id inválido",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "401", description = "Sin token",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public List<Responses.ViajeResumenResponse> resumen(
            @Parameter(description = "Ids de viaje separados por coma", required = true,
                    example = "50000000-0000-4000-8000-000000000001")
            @RequestParam List<UUID> ids) {
        return consultaViajes.resumen(ids);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un viaje", description = "Incluye los registros de salida y llegada si existen.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Viaje",
                    content = @Content(schema = @Schema(implementation = Responses.ViajeResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public Responses.ViajeResponse detalle(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return consultaViajes.detalle(id, convertidor.desde(jwt));
    }

    @GetMapping("/activos")
    @Operation(summary = "Viajes activos de un bus (interno)",
            description = "PROGRAMADO o EN_TRANSITO del bus. Lo usa Flota antes de desactivar un bus. "
                    + "Requiere cabecera X-Clave-Interna (sin Authorization).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Viajes activos",
                    content = @Content(schema = @Schema(implementation = Responses.ViajeResponse.class))),
            @ApiResponse(responseCode = "401", description = "Clave interna incorrecta",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public List<Responses.ViajeResponse> activos(
            @Parameter(description = "Identificador del bus", required = true)
            @RequestParam UUID idBus) {
        return consultaViajes.activosPorBus(idBus);
    }

    @PostMapping
    @Operation(summary = "Programar viaje regular (admin de sucursal)",
            description = "Valida bus y chofer activos, de la sucursal, licencia vigente y sin traslapes. "
                    + "Evento: ViajeProgramado.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Viaje creado",
                    content = @Content(schema = @Schema(implementation = Responses.ViajeResponse.class))),
            @ApiResponse(responseCode = "409", description = "CONFLICTO_HORARIO o CAPACIDAD_INSUFICIENTE",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public ResponseEntity<Responses.ViajeResponse> crear(
            @Valid @RequestBody Requests.ViajeRegularRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Responses.ViajeResponse resp = programacionViajes.crearRegular(request, convertidor.desde(jwt));
        return ResponseEntity.created(URI.create("/viajes/" + resp.id())).body(resp);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar viaje (admin de sucursal)",
            description = "Cambia bus, chofer o fechas (no el tipo). Si se cambia el bus de un viaje regular "
                    + "con boletos vendidos, consulta a Boletos asiento-maximo (409 CAPACIDAD_INSUFICIENTE). "
                    + "Evento: ViajeActualizado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Viaje actualizado",
                    content = @Content(schema = @Schema(implementation = Responses.ViajeResponse.class))),
            @ApiResponse(responseCode = "409", description = "Viaje no modificable o capacidad insuficiente",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "503", description = "Boletos no responde (falla cerrada)",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public Responses.ViajeResponse actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody Requests.ViajeUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return programacionViajes.actualizar(id, request, convertidor.desde(jwt));
    }

    @PatchMapping("/{id}/asignacion")
    @Operation(summary = "Asignar bus y chofer a un alquiler pendiente",
            description = "PENDIENTE_ASIGNACION → PROGRAMADO. Evento: ViajeActualizado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Asignado",
                    content = @Content(schema = @Schema(implementation = Responses.ViajeResponse.class))),
            @ApiResponse(responseCode = "409", description = "No está pendiente o no es de alquiler",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public Responses.ViajeResponse asignar(
            @PathVariable UUID id,
            @Valid @RequestBody Requests.AsignacionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return programacionViajes.asignar(id, request, convertidor.desde(jwt));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar viaje (admin de sucursal)",
            description = "Solo si no ha iniciado ni finalizado (409 VIAJE_NO_ELIMINABLE). "
                    + "Propaga ViajeEliminado (saga §4.3).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Eliminado"),
            @ApiResponse(responseCode = "409", description = "El viaje ya inició o finalizó",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                                    value = "{\"codigo\":\"VIAJE_NO_ELIMINABLE\"}")))
    })
    public ResponseEntity<Void> eliminar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        programacionViajes.eliminar(id, convertidor.desde(jwt));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/salida")
    @Operation(summary = "Registrar salida (SUC o CHO del viaje)",
            description = "PROGRAMADO → EN_TRANSITO. Registro inmutable. Evento: SalidaRegistrada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Salida registrada",
                    content = @Content(schema = @Schema(implementation = Responses.ViajeResponse.class))),
            @ApiResponse(responseCode = "409", description = "Estado incorrecto o salida ya registrada",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public Responses.ViajeResponse registrarSalida(
            @PathVariable UUID id,
            @Valid @RequestBody Requests.SalidaRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return registroViaje.registrarSalida(id, request, convertidor.desde(jwt));
    }

    @PostMapping("/{id}/llegada")
    @Operation(summary = "Registrar llegada (SUC o CHO del viaje)",
            description = "EN_TRANSITO → FINALIZADO. Registro inmutable con distancia, combustible y salario. "
                    + "Evento: LlegadaRegistrada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Llegada registrada; viaje FINALIZADO",
                    content = @Content(schema = @Schema(implementation = Responses.ViajeResponse.class))),
            @ApiResponse(responseCode = "409", description = "Estado incorrecto o llegada ya registrada",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public Responses.ViajeResponse registrarLlegada(
            @PathVariable UUID id,
            @Valid @RequestBody Requests.LlegadaRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return registroViaje.registrarLlegada(id, request, convertidor.desde(jwt));
    }
}
