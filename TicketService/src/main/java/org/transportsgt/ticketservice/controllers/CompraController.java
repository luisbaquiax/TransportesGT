package org.transportsgt.ticketservice.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.transportsgt.ticketservice.config.security.ConvertidorUsuarioAutenticado;
import org.transportsgt.ticketservice.dto.request.CompraRequest;
import org.transportsgt.ticketservice.dto.response.CompraResponse;
import org.transportsgt.ticketservice.dto.response.PaginaResponse;
import org.transportsgt.ticketservice.dto.response.ResultadoCompra;
import org.transportsgt.ticketservice.exception.RespuestaError;
import org.transportsgt.ticketservice.models.EstadoCompra;
import org.transportsgt.ticketservice.services.compra.CompraEnLineaService;
import org.transportsgt.ticketservice.services.consulta.ConsultaComprasService;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/boletos/compras")
@RequiredArgsConstructor
@Tag(name = "Compras", description = "Compra en línea (saga con Cartera) y consultas de compras")
public class CompraController {

    private final ConvertidorUsuarioAutenticado convertidorUsuarioAutenticado;

    private final CompraEnLineaService compraEnLinea;
    private final ConsultaComprasService consultaCompras;

    @PostMapping
    @Operation(summary = "Comprar en línea",
            description = "Saga: reserva los asientos, cobra en la cartera y confirma. idCompra lo genera quien llama y es "
                    + "la clave de idempotencia: reenviar la misma compra no cobra dos veces. El NIT y el nombre salen "
                    + "del perfil del comprador. Si Cartera no responde, la compra queda RESERVADA (202) y el job de "
                    + "recuperación la confirma o la libera.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    examples = @ExampleObject(value = """
                            {"idCompra":"6f1c2a7e-3b4d-4e5f-8a9b-0c1d2e3f4a5b","idViaje":"5d1c2a7e-3b4d-4e5f-8a9b-0c1d2e3f4a5b",
                            "asientos":[12,13],"fechaPago":"2026-10-20"}"""))))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Compra CONFIRMADA",
                    content = @Content(schema = @Schema(implementation = CompraResponse.class),
                            examples = @ExampleObject(value = EjemplosApi.COMPRA))),
            @ApiResponse(responseCode = "200", description = "idCompra repetido: la compra ya estaba confirmada"),
            @ApiResponse(responseCode = "202", description = "Pago pendiente de verificar: la compra quedó RESERVADA",
                    content = @Content(schema = @Schema(implementation = CompraResponse.class),
                            examples = @ExampleObject(value = EjemplosApi.COMPRA_RESERVADA))),
            @ApiResponse(responseCode = "400", description = "Asientos inválidos",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_400))),
            @ApiResponse(responseCode = "403", description = "Cuenta desactivada",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_403_INACTIVA))),
            @ApiResponse(responseCode = "409", description = "Asiento ocupado, viaje no disponible o idCompra con otros datos",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = {@ExampleObject(name = "ASIENTO_NO_DISPONIBLE", value = EjemplosApi.ERROR_409_ASIENTO),
                                    @ExampleObject(name = "VIAJE_NO_DISPONIBLE", value = EjemplosApi.ERROR_409_VIAJE),
                                    @ExampleObject(name = "COMPRA_DUPLICADA", value = EjemplosApi.ERROR_409_COMPRA)})),
            @ApiResponse(responseCode = "422", description = "Saldo insuficiente o perfil sin NIT",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = {@ExampleObject(name = "SALDO_INSUFICIENTE", value = EjemplosApi.ERROR_422_SALDO),
                                    @ExampleObject(name = "PERFIL_INCOMPLETO", value = EjemplosApi.ERROR_422_PERFIL)}))
    })
    public ResponseEntity<CompraResponse> comprar(@Valid @RequestBody CompraRequest request,
                                                  @AuthenticationPrincipal Jwt jwt) {
        ResultadoCompra resultado = compraEnLinea.comprar(convertidorUsuarioAutenticado.desde(jwt), jwt.getTokenValue(), request);
        return switch (resultado.tipo()) {
            case CONFIRMADA -> ResponseEntity.created(ubicacion(resultado.compra())).body(resultado.compra());
            case EXISTENTE -> ResponseEntity.ok(resultado.compra());
            case PENDIENTE -> ResponseEntity.status(HttpStatus.ACCEPTED)
                    .location(ubicacion(resultado.compra())).body(resultado.compra());
        };
    }

    @GetMapping("/mias")
    @Operation(summary = "Mi historial de compras",
            description = "Compras en línea del usuario y ventas de mostrador hechas con su NIT (también las anteriores a "
                    + "crear la cuenta). El NIT sale del perfil, nunca de un parámetro. Filtro opcional por estado.")
    @ApiResponse(responseCode = "200", description = "Página de compras")
    public PaginaResponse<CompraResponse> mias(
            @Parameter(description = "RESERVADA, CONFIRMADA o ANULADA") @RequestParam(required = false) EstadoCompra estado,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano,
            @AuthenticationPrincipal Jwt jwt) {
        return consultaCompras.mias(convertidorUsuarioAutenticado.desde(jwt), estado, pagina, tamano);
    }

    @GetMapping("/{idCompra}")
    @Operation(summary = "Detalle de una compra",
            description = "El comprador ve las suyas; SUC y CAJ, las de su sucursal; SIS, todas. Sirve también para "
                    + "consultar una compra RESERVADA (202).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Compra",
                    content = @Content(schema = @Schema(implementation = CompraResponse.class),
                            examples = @ExampleObject(value = EjemplosApi.COMPRA))),
            @ApiResponse(responseCode = "404", description = "No existe o no le corresponde",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_404_COMPRA)))
    })
    public CompraResponse detalle(@PathVariable UUID idCompra, @AuthenticationPrincipal Jwt jwt) {
        return consultaCompras.detalle(convertidorUsuarioAutenticado.desde(jwt), idCompra);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CAJERO', 'ADMIN_SUCURSAL')")
    @Operation(summary = "Compras de mi sucursal", description = "Filtros opcionales por viaje y estado.")
    @ApiResponse(responseCode = "200", description = "Página de compras")
    public PaginaResponse<CompraResponse> listado(
            @Parameter(description = "Viaje") @RequestParam(required = false) UUID idViaje,
            @Parameter(description = "RESERVADA, CONFIRMADA o ANULADA") @RequestParam(required = false) EstadoCompra estado,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano,
            @AuthenticationPrincipal Jwt jwt) {
        return consultaCompras.listado(convertidorUsuarioAutenticado.desde(jwt), idViaje, estado, pagina, tamano);
    }

    private URI ubicacion(CompraResponse compra) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/boletos/compras/{id}").buildAndExpand(compra.idCompra()).toUri();
    }
}
