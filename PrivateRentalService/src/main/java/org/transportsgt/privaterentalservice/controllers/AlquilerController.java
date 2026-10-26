package org.transportsgt.privaterentalservice.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;
import org.transportsgt.privaterentalservice.config.OpenApiConfig;
import org.transportsgt.privaterentalservice.config.security.ConvertidorUsuarioAutenticado;
import org.transportsgt.privaterentalservice.dto.request.CotizacionRequest;
import org.transportsgt.privaterentalservice.dto.request.FiltroAlquileres;
import org.transportsgt.privaterentalservice.dto.request.PagoAlquilerRequest;
import org.transportsgt.privaterentalservice.dto.request.SolicitudAlquilerRequest;
import org.transportsgt.privaterentalservice.dto.request.SolicitudMostradorRequest;
import org.transportsgt.privaterentalservice.dto.response.AlquilerResponse;
import org.transportsgt.privaterentalservice.dto.response.PaginaResponse;
import org.transportsgt.privaterentalservice.exception.RespuestaError;
import org.transportsgt.privaterentalservice.models.EstadoAlquiler;
import org.transportsgt.privaterentalservice.services.consulta.ConsultaAlquileresService;
import org.transportsgt.privaterentalservice.services.cotizacion.CotizacionAlquilerService;
import org.transportsgt.privaterentalservice.services.pago.PagoAlquilerService;
import org.transportsgt.privaterentalservice.services.solicitud.SolicitudAlquilerService;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/alquileres")
@RequiredArgsConstructor
@Tag(name = "Alquileres", description = "Alquileres de buses privados: solicitud, cotización y pago")
public class AlquilerController {

    private final SolicitudAlquilerService solicitudService;
    private final CotizacionAlquilerService cotizacionService;
    private final PagoAlquilerService pagoService;
    private final ConsultaAlquileresService consultaService;
    private final ConvertidorUsuarioAutenticado convertidor;

    @PostMapping("/solicitudes")
    @Operation(summary = "Solicitud en línea",
            description = "Calcula el precio estimado con la tarifa vigente y publica AlquilerSolicitado. "
                    + "El NIT y el nombre salen del perfil del cliente, no del cuerpo.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Alquiler creado",
                    content = @Content(schema = @Schema(implementation = AlquilerResponse.class))),
            @ApiResponse(responseCode = "400", description = "Faltan coordenadas y distancia, o fechas inválidas",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "401", description = "Sin token",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = "{\"codigo\":\"NO_AUTENTICADO\"}"))),
            @ApiResponse(responseCode = "422", description = "Perfil sin NIT",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = "{\"codigo\":\"PERFIL_INCOMPLETO\"}")))
    })
    public ResponseEntity<AlquilerResponse> solicitarEnLinea(
            @Valid @RequestBody SolicitudAlquilerRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        AlquilerResponse resp = solicitudService.solicitarEnLinea(request, convertidor.desde(jwt));
        return ResponseEntity.created(URI.create("/alquileres/" + resp.id())).body(resp);
    }

    @PostMapping("/mostrador")
    @Operation(summary = "Solicitud presencial",
            description = "Mismo flujo que la en línea pero con NIT y nombre del cliente del mostrador. "
                    + "El canal es MOSTRADOR y no requiere cuenta en el sistema.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Alquiler creado",
                    content = @Content(schema = @Schema(implementation = AlquilerResponse.class))),
            @ApiResponse(responseCode = "403", description = "No es de sucursal ni cajero",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = "{\"codigo\":\"OPERACION_NO_AUTORIZADA\"}")))
    })
    public ResponseEntity<AlquilerResponse> solicitarMostrador(
            @Valid @RequestBody SolicitudMostradorRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        AlquilerResponse resp = solicitudService.solicitarMostrador(request, convertidor.desde(jwt));
        return ResponseEntity.created(URI.create("/alquileres/" + resp.id())).body(resp);
    }

    @PostMapping("/{id}/cotizacion")
    @Operation(summary = "Cotizar (admin de sucursal)",
            description = "Fija precio final y llegada estimada. SOLICITADO o COTIZADO → COTIZADO. "
                    + "Publica AlquilerCotizado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cotizado",
                    content = @Content(schema = @Schema(implementation = AlquilerResponse.class))),
            @ApiResponse(responseCode = "409", description = "Estado no permite cotizar",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = "{\"codigo\":\"TRANSICION_INVALIDA\"}")))
    })
    public AlquilerResponse cotizar(
            @PathVariable UUID id,
            @Valid @RequestBody CotizacionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return cotizacionService.cotizar(id, request, convertidor.desde(jwt));
    }

    @PostMapping("/{id}/pago")
    @Operation(summary = "Pago en línea con cartera",
            description = "Solo el cliente dueño del alquiler en línea, en estado COTIZADO. "
                    + "Saga: llama a Cartera con Clave-Idempotencia=idAlquiler; si no responde, 503 sin confirmar. "
                    + "Publica AlquilerConfirmado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Confirmado",
                    content = @Content(schema = @Schema(implementation = AlquilerResponse.class))),
            @ApiResponse(responseCode = "403", description = "No es el dueño",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "409", description = "No está COTIZADO",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = "{\"codigo\":\"TRANSICION_INVALIDA\"}"))),
            @ApiResponse(responseCode = "422", description = "Cartera rechazó el cobro (status y código de Cartera)",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = "{\"codigo\":\"SALDO_INSUFICIENTE\"}"))),
            @ApiResponse(responseCode = "503", description = "Cartera no disponible",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = "{\"codigo\":\"SERVICIO_NO_DISPONIBLE\"}")))
    })
    public AlquilerResponse pagarEnLinea(
            @PathVariable UUID id,
            @Valid @RequestBody PagoAlquilerRequest request,
            @RequestHeader("Authorization") String authorization,
            @AuthenticationPrincipal Jwt jwt) {
        return pagoService.pagarEnLinea(id, request, convertidor.desde(jwt), authorization);
    }

    @PostMapping("/{id}/pago-mostrador")
    @Operation(summary = "Pago en efectivo en mostrador",
            description = "Admin de sucursal o cajero. Sin cartera: idPago queda null. "
                    + "Publica AlquilerConfirmado.")
    public AlquilerResponse pagarMostrador(
            @PathVariable UUID id,
            @Valid @RequestBody PagoAlquilerRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return pagoService.pagarMostrador(id, request, convertidor.desde(jwt));
    }

    @GetMapping("/mios")
    @Operation(summary = "Mi historial",
            description = "Alquileres del usuario autenticado (en línea) y los hechos con su NIT en mostrador.")
    public PaginaResponse<AlquilerResponse> mios(
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano,
            @AuthenticationPrincipal Jwt jwt) {
        return consultaService.mios(convertidor.desde(jwt), PageRequest.of(pagina, tamano));
    }

    @GetMapping
    @Operation(summary = "Listar alquileres",
            description = "SUC y CAJ ven los de su sucursal; SIS ve todos. desde/hasta son días de Guatemala (incluidos). "
                    + "sinAsignar=true lista los confirmados que aún no tienen bus.")
    public PaginaResponse<AlquilerResponse> listar(
            @RequestParam(required = false) EstadoAlquiler estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Boolean sinAsignar,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano,
            @AuthenticationPrincipal Jwt jwt) {
        return consultaService.listar(new FiltroAlquileres(estado, desde, hasta, sinAsignar), convertidor.desde(jwt),
                PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, "fechaHoraSalida").and(Sort.by("id"))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle con historial de cotizaciones",
            description = "El cliente ve los suyos (en línea o de mostrador con el NIT de su perfil); "
                    + "SUC y CAJ, los de su sucursal; SIS ve todos.",
            security = {@SecurityRequirement(name = OpenApiConfig.ESQUEMA_JWT)})
    public AlquilerResponse detalle(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return consultaService.detalle(id, convertidor.desde(jwt));
    }
}
