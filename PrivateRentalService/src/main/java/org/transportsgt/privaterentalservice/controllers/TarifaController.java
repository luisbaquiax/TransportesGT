package org.transportsgt.privaterentalservice.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.transportsgt.privaterentalservice.config.security.authentication.ConvertidorUsuarioAutenticado;
import org.transportsgt.privaterentalservice.dto.request.TarifaRequest;
import org.transportsgt.privaterentalservice.dto.response.TarifaResponse;
import org.transportsgt.privaterentalservice.exception.RespuestaError;
import org.transportsgt.privaterentalservice.services.tarifa.TarifaAlquilerService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/alquileres/tarifas")
@RequiredArgsConstructor
@Tag(name = "Tarifas de alquiler", description = "Tarifas del precio estimado (precio por km y por día)")
public class TarifaController {

    private final TarifaAlquilerService tarifaService;
    private final ConvertidorUsuarioAutenticado convertidor;

    @GetMapping("/vigente")
    @Operation(summary = "Tarifa vigente",
            description = "La más reciente con vigenteDesde ≤ fecha (hoy en Guatemala si no se indica). "
                    + "Cualquier usuario autenticado: el formulario de solicitud la usa para mostrar el estimado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tarifa",
                    content = @Content(schema = @Schema(implementation = TarifaResponse.class))),
            @ApiResponse(responseCode = "404", description = "No hay tarifa a esa fecha",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = "{\"codigo\":\"SIN_TARIFA\"}")))
    })
    public TarifaResponse vigente(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return tarifaService.vigente(fecha);
    }

    @GetMapping
    @Operation(summary = "Historial de tarifas (SIS)", description = "De la más reciente a la más antigua.")
    public List<TarifaResponse> historial() {
        return tarifaService.historial();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar tarifa (SIS)",
            description = "Crea la tarifa desde vigenteDesde (hoy o después). Si ya hay una con esa fecha, la reemplaza.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Registrada",
                    content = @Content(schema = @Schema(implementation = TarifaResponse.class))),
            @ApiResponse(responseCode = "400", description = "Vigencia en el pasado o valores inválidos",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "403", description = "No es administrador de sistema",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public TarifaResponse registrar(@Valid @RequestBody TarifaRequest request, @AuthenticationPrincipal Jwt jwt) {
        return tarifaService.registrar(request, convertidor.desde(jwt));
    }
}
