package org.transportsgt.ticketservice.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.transportsgt.ticketservice.config.security.UsuarioAutenticado;
import org.transportsgt.ticketservice.dto.request.VentaMostradorRequest;
import org.transportsgt.ticketservice.dto.response.CompraResponse;
import org.transportsgt.ticketservice.dto.response.Resultado;
import org.transportsgt.ticketservice.exception.RespuestaError;
import org.transportsgt.ticketservice.services.VentaMostradorService;

import java.net.URI;

@RestController
@RequestMapping("/boletos/ventas-mostrador")
@RequiredArgsConstructor
@Tag(name = "Venta en mostrador", description = "Venta presencial por cajero o administrador de sucursal")
public class VentaMostradorController {

    private final VentaMostradorService ventaMostrador;

    @PostMapping
    @PreAuthorize("hasAnyRole('CAJERO', 'ADMIN_SUCURSAL')")
    @Operation(summary = "Vender en mostrador",
            description = "Reserva y confirma en una sola operación (no usa la cartera). El viaje debe ser de la sucursal "
                    + "del vendedor. Sin NIT se registra como consumidor final (CF). Publica VentaBoletosRegistrada. "
                    + "Un idCompra repetido con los mismos datos devuelve la venta original (200).",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    examples = @ExampleObject(value = """
                            {"idCompra":"9b8a7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d","idViaje":"5d1c2a7e-3b4d-4e5f-8a9b-0c1d2e3f4a5b",
                            "asientos":[20,21],"nitComprador":"1234567-K","nombreComprador":"Carlos Méndez","fechaPago":"2026-10-20"}"""))))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Venta registrada",
                    content = @Content(schema = @Schema(implementation = CompraResponse.class),
                            examples = @ExampleObject(value = EjemplosApi.COMPRA))),
            @ApiResponse(responseCode = "200", description = "idCompra repetido: venta original"),
            @ApiResponse(responseCode = "400", description = "Asientos o NIT inválidos",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_400))),
            @ApiResponse(responseCode = "403", description = "El viaje es de otra sucursal",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_403_SUCURSAL))),
            @ApiResponse(responseCode = "409", description = "Asiento ocupado, viaje no disponible o idCompra con otros datos",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = {@ExampleObject(name = "ASIENTO_NO_DISPONIBLE", value = EjemplosApi.ERROR_409_ASIENTO),
                                    @ExampleObject(name = "VIAJE_NO_DISPONIBLE", value = EjemplosApi.ERROR_409_VIAJE)}))
    })
    public ResponseEntity<CompraResponse> vender(@Valid @RequestBody VentaMostradorRequest request,
                                                 @AuthenticationPrincipal Jwt jwt) {
        Resultado<CompraResponse> resultado = ventaMostrador.vender(UsuarioAutenticado.de(jwt), request);
        if (!resultado.creado()) {
            return ResponseEntity.ok(resultado.valor());
        }
        return ResponseEntity.created(ubicacion(resultado.valor())).body(resultado.valor());
    }

    private URI ubicacion(CompraResponse compra) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/boletos/compras/{id}").buildAndExpand(compra.idCompra()).toUri();
    }
}
