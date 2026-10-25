package org.transportsgt.customerwalletservice.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.transportsgt.customerwalletservice.config.OpenApiConfig;
import org.transportsgt.customerwalletservice.config.security.FiltroClaveInterna;
import org.transportsgt.customerwalletservice.config.security.ConvertidorUsuarioAutenticado;
import org.transportsgt.customerwalletservice.dto.request.PagoRequest;
import org.transportsgt.customerwalletservice.dto.request.RecargaRequest;
import org.transportsgt.customerwalletservice.dto.response.CarteraResponse;
import org.transportsgt.customerwalletservice.dto.response.MovimientoResponse;
import org.transportsgt.customerwalletservice.dto.response.PagoConsultaResponse;
import org.transportsgt.customerwalletservice.dto.response.PagoResponse;
import org.transportsgt.customerwalletservice.dto.response.PaginaResponse;
import org.transportsgt.customerwalletservice.dto.response.Resultado;
import org.transportsgt.customerwalletservice.exception.RespuestaError;
import org.transportsgt.customerwalletservice.models.TipoMovimiento;
import org.transportsgt.customerwalletservice.services.CarteraService;
import org.transportsgt.customerwalletservice.services.PagoService;
import org.transportsgt.eventos.payload.TipoReferencia;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/cartera")
@RequiredArgsConstructor
@Tag(name = "Cartera", description = "Saldo, recargas, movimientos y pagos de la cartera digital")
public class CarteraController {

    private final ConvertidorUsuarioAutenticado convertidorUsuarioAutenticado;

    public static final String CABECERA_IDEMPOTENCIA = "Clave-Idempotencia";

    private final CarteraService carteraService;
    private final PagoService pagoService;

    @GetMapping("/yo")
    @Operation(summary = "Mi saldo")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cartera",
                    content = @Content(schema = @Schema(implementation = CarteraResponse.class),
                            examples = @ExampleObject(value = EjemplosApi.CARTERA))),
            @ApiResponse(responseCode = "401", description = "Sin token o token inválido",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_401))),
            @ApiResponse(responseCode = "404", description = "El perfil aún no existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_404_CLIENTE)))
    })
    public CarteraResponse obtener(@AuthenticationPrincipal Jwt jwt) {
        return carteraService.obtener(convertidorUsuarioAutenticado.desde(jwt));
    }

    @PostMapping("/recargas")
    @Operation(summary = "Recargar saldo",
            description = "Cabecera Clave-Idempotencia obligatoria: si se repite con el mismo monto devuelve la recarga "
                    + "original (200) sin sumar el saldo dos veces. Publica CarteraRecargada.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    examples = @ExampleObject(value = "{\"monto\":\"200.00\",\"fechaRecarga\":\"2026-10-20\"}"))))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Recarga aplicada",
                    content = @Content(schema = @Schema(implementation = MovimientoResponse.class),
                            examples = @ExampleObject(value = EjemplosApi.RECARGA))),
            @ApiResponse(responseCode = "200", description = "Clave repetida: recarga original",
                    content = @Content(schema = @Schema(implementation = MovimientoResponse.class),
                            examples = @ExampleObject(value = EjemplosApi.RECARGA))),
            @ApiResponse(responseCode = "400", description = "Falta la clave o el monto es inválido",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_400_CLAVE))),
            @ApiResponse(responseCode = "403", description = "Cuenta desactivada",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_403_INACTIVA))),
            @ApiResponse(responseCode = "409", description = "La clave ya se usó con otra cartera u otro monto",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_409_CLAVE)))
    })
    public ResponseEntity<MovimientoResponse> recargar(
            @Parameter(description = "UUID generado por el cliente", example = "3f2a1b0c-9d8e-4f7a-b6c5-d4e3f2a1b0c9")
            @RequestHeader(value = CABECERA_IDEMPOTENCIA, required = false) String claveIdempotencia,
            @Valid @RequestBody RecargaRequest request, @AuthenticationPrincipal Jwt jwt) {
        Resultado<MovimientoResponse> resultado =
                carteraService.recargar(convertidorUsuarioAutenticado.desde(jwt), claveIdempotencia, request);
        return ResponseEntity.status(resultado.creado() ? HttpStatus.CREATED : HttpStatus.OK).body(resultado.valor());
    }

    @GetMapping("/yo/movimientos")
    @Operation(summary = "Mis movimientos", description = "Recargas, pagos y reembolsos, el más reciente primero.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de movimientos",
                    content = @Content(examples = @ExampleObject(value = EjemplosApi.MOVIMIENTOS))),
            @ApiResponse(responseCode = "400", description = "Rango de fechas o paginación inválidos",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_400)))
    })
    public PaginaResponse<MovimientoResponse> movimientos(
            @Parameter(description = "RECARGA, PAGO o REEMBOLSO") @RequestParam(required = false) TipoMovimiento tipo,
            @Parameter(example = "2026-10-01") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @Parameter(example = "2026-10-31") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano,
            @AuthenticationPrincipal Jwt jwt) {
        return carteraService.movimientos(convertidorUsuarioAutenticado.desde(jwt), tipo, desde, hasta, pagina, tamano);
    }

    @PostMapping("/pagos")
    @Operation(summary = "Cobrar una compra o un alquiler (Interno: Boletos, Alquileres)",
            description = "Lo llaman Boletos y Alquileres reenviando el JWT del usuario: el cliente sale del token. "
                    + "Clave-Idempotencia obligatoria e igual a idReferencia. Si el pago ya existía devuelve el "
                    + "original (200). No acepta X-Clave-Interna. No publica evento.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    examples = @ExampleObject(value = """
                            {"tipoReferencia":"BOLETOS","idReferencia":"6f1c2a7e-3b4d-4e5f-8a9b-0c1d2e3f4a5b",
                            "monto":"170.00","fechaPago":"2026-10-21","descripcion":"2 boletos Xela → Capital"}"""))))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pago aplicado",
                    content = @Content(schema = @Schema(implementation = PagoResponse.class),
                            examples = @ExampleObject(value = EjemplosApi.PAGO))),
            @ApiResponse(responseCode = "200", description = "El pago ya existía: se devuelve el original",
                    content = @Content(schema = @Schema(implementation = PagoResponse.class),
                            examples = @ExampleObject(value = EjemplosApi.PAGO))),
            @ApiResponse(responseCode = "400", description = "Falta la clave o no es igual a idReferencia",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_400_CLAVE))),
            @ApiResponse(responseCode = "403", description = "Cuenta desactivada",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_403_INACTIVA))),
            @ApiResponse(responseCode = "409", description = "Ya hay un pago de esa referencia con otros datos",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_409_CLAVE))),
            @ApiResponse(responseCode = "422", description = "Saldo insuficiente o perfil sin NIT",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = {@ExampleObject(name = "SALDO_INSUFICIENTE", value = EjemplosApi.ERROR_422_SALDO),
                                    @ExampleObject(name = "PERFIL_INCOMPLETO", value = EjemplosApi.ERROR_422_PERFIL)}))
    })
    public ResponseEntity<PagoResponse> pagar(
            @Parameter(description = "Igual a idReferencia (idCompra o idAlquiler)")
            @RequestHeader(value = CABECERA_IDEMPOTENCIA, required = false) String claveIdempotencia,
            @Valid @RequestBody PagoRequest request, @AuthenticationPrincipal Jwt jwt) {
        Resultado<PagoResponse> resultado = pagoService.pagar(convertidorUsuarioAutenticado.desde(jwt), claveIdempotencia, request);
        if (!resultado.creado()) {
            return ResponseEntity.ok(resultado.valor());
        }
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .queryParam("tipoReferencia", request.tipoReferencia())
                .queryParam("idReferencia", request.idReferencia())
                .build().toUri();
        return ResponseEntity.created(ubicacion).body(resultado.valor());
    }

    @GetMapping("/pagos")
    @Operation(summary = "Consultar el pago de una compra o alquiler (Interno: Boletos, Alquileres)",
            description = "Para la recuperación de la saga. Con JWT solo ve los pagos de la cartera del usuario; con la "
                    + "cabecera X-Clave-Interna (jobs sin usuario) ve cualquiera. Ruta no publicada en el gateway.",
            security = {@SecurityRequirement(name = OpenApiConfig.ESQUEMA_JWT)},
            parameters = @Parameter(in = ParameterIn.HEADER, name = FiltroClaveInterna.CABECERA, required = false,
                    description = "Secreto compartido de los jobs (alternativa al JWT)",
                    schema = @Schema(type = "string")))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pago",
                    content = @Content(schema = @Schema(implementation = PagoConsultaResponse.class),
                            examples = @ExampleObject(value = EjemplosApi.PAGO_CONSULTA))),
            @ApiResponse(responseCode = "401", description = "Sin token, o X-Clave-Interna incorrecta",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_401))),
            @ApiResponse(responseCode = "404", description = "No hay pago (o es de otra cartera)",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_404_PAGO)))
    })
    public PagoConsultaResponse consultarPago(
            @Parameter(example = "BOLETOS") @RequestParam TipoReferencia tipoReferencia,
            @Parameter(example = "6f1c2a7e-3b4d-4e5f-8a9b-0c1d2e3f4a5b") @RequestParam UUID idReferencia,
            Authentication autenticacion) {
        UUID idUsuario = autenticacion instanceof JwtAuthenticationToken token
                ? convertidorUsuarioAutenticado.desde(token.getToken()).id()
                : null;
        return pagoService.consultarPago(idUsuario, tipoReferencia, idReferencia);
    }
}
