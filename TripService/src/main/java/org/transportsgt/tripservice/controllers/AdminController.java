package org.transportsgt.tripservice.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.transportsgt.tripservice.dto.response.Responses;
import org.transportsgt.tripservice.exception.RespuestaError;
import org.transportsgt.tripservice.services.RepublicacionEventosService;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_SISTEMA')")
@Tag(name = "Administración", description = "Operaciones de mantenimiento (solo ADMIN_SISTEMA)")
public class AdminController {

    private final RepublicacionEventosService republicacionService;

    @PostMapping("/republicar-eventos")
    @Operation(summary = "Republicar rutas y viajes",
            description = "Guarda en el outbox RutaCreada y ViajeProgramado de todo lo existente para poblar las "
                    + "proyecciones de Boletos, Alquileres y Reportes (los datos iniciales no generan eventos). "
                    + "Es idempotente; no republica SalidaRegistrada ni LlegadaRegistrada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Eventos guardados en el outbox",
                    content = @Content(schema = @Schema(implementation = Responses.RepublicacionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sin token",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "403", description = "No es ADMIN_SISTEMA",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public Responses.RepublicacionResponse republicar() {
        return republicacionService.republicar();
    }
}
