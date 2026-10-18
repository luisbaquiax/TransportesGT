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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
import org.springframework.web.bind.annotation.RestController;
import org.transportsgt.privaterentalservice.config.OpenApiConfig;
import org.transportsgt.privaterentalservice.config.security.UsuarioAutenticado;
import org.transportsgt.privaterentalservice.dto.request.CotizacionRequest;
import org.transportsgt.privaterentalservice.dto.request.PagoAlquilerRequest;
import org.transportsgt.privaterentalservice.dto.request.SolicitudAlquilerRequest;
import org.transportsgt.privaterentalservice.dto.request.SolicitudMostradorRequest;
import org.transportsgt.privaterentalservice.dto.response.AlquilerResponse;
import org.transportsgt.privaterentalservice.dto.response.PaginaResponse;
import org.transportsgt.privaterentalservice.dto.response.RepublicacionResponse;
import org.transportsgt.privaterentalservice.exception.RespuestaError;
import org.transportsgt.privaterentalservice.models.EstadoAlquiler;
import org.transportsgt.privaterentalservice.services.AlquilerService;
import org.transportsgt.privaterentalservice.services.RepublicacionEventosService;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/alquileres")
@RequiredArgsConstructor
@Tag(name = "Alquileres", description = "Alquileres de buses privados: solicitud, cotización y pago")
public class AlquilerController {

    private final AlquilerService alquilerService;
    private final RepublicacionEventosService republicacionService;

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
        AlquilerResponse resp = alquilerService.solicitarEnLinea(request, UsuarioAutenticado.de(jwt));
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
        AlquilerResponse resp = alquilerService.solicitarMostrador(request, UsuarioAutenticado.de(jwt));
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
        return alquilerService.cotizar(id, request, UsuarioAutenticado.de(jwt));
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
            @ApiResponse(responseCode = "503", description = "Cartera no disponible",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = "{\"codigo\":\"SERVICIO_NO_DISPONIBLE\"}")))
    })
    public AlquilerResponse pagarEnLinea(
            @PathVariable UUID id,
            @Valid @RequestBody PagoAlquilerRequest request,
            @RequestHeader("Authorization") String authorization,
            @AuthenticationPrincipal Jwt jwt) {
        return alquilerService.pagarEnLinea(id, request, UsuarioAutenticado.de(jwt), authorization);
    }

    @PostMapping("/{id}/pago-mostrador")
    @Operation(summary = "Pago en efectivo en mostrador",
            description = "Admin de sucursal o cajero. Sin cartera: idPago queda null. "
                    + "Publica AlquilerConfirmado.")
    public AlquilerResponse pagarMostrador(
            @PathVariable UUID id,
            @Valid @RequestBody PagoAlquilerRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return alquilerService.pagarMostrador(id, request, UsuarioAutenticado.de(jwt));
    }

    @GetMapping("/mios")
    @Operation(summary = "Mi historial",
            description = "Alquileres del usuario autenticado (en línea) y los hechos con su NIT en mostrador.")
    public PaginaResponse<AlquilerResponse> mios(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano,
            @AuthenticationPrincipal Jwt jwt) {
        Page<AlquilerResponse> page = alquilerService.mios(UsuarioAutenticado.de(jwt),
                PageRequest.of(pagina, Math.min(tamano, 100), Sort.by(Sort.Direction.DESC, "creadoEn")));
        return paginar(page);
    }

    @GetMapping
    @Operation(summary = "Listar alquileres",
            description = "SUC y CAJ ven los de su sucursal; SIS ve todos. sinAsignar=true lista confirmados sin viaje.")
    public PaginaResponse<AlquilerResponse> listar(
            @RequestParam(required = false) EstadoAlquiler estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Boolean sinAsignar,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano,
            @AuthenticationPrincipal Jwt jwt) {
        Page<AlquilerResponse> page = alquilerService.listar(estado, desde, hasta, sinAsignar,
                UsuarioAutenticado.de(jwt),
                PageRequest.of(pagina, Math.min(tamano, 100), Sort.by(Sort.Direction.DESC, "fechaHoraSalida")));
        return paginar(page);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle con historial de cotizaciones",
            description = "El cliente solo ve los suyos; SUC ve los de su sucursal; SIS ve todos.",
            security = {@SecurityRequirement(name = OpenApiConfig.ESQUEMA_JWT)})
    public AlquilerResponse detalle(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return alquilerService.detalle(id, UsuarioAutenticado.de(jwt));
    }

    @PostMapping("/admin/republicar-eventos")
    @Operation(summary = "Republicar eventos (SIS)",
            description = "Encola AlquilerSolicitado de todo lo existente para poblar proyecciones. Idempotente.")
    public RepublicacionResponse republicar(@AuthenticationPrincipal Jwt jwt) {
        return republicacionService.republicar();
    }

    private static <T> PaginaResponse<T> paginar(Page<T> page) {
        return new PaginaResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
