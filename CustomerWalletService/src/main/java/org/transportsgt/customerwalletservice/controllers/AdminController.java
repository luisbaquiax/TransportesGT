package org.transportsgt.customerwalletservice.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.transportsgt.customerwalletservice.dto.response.RepublicacionResponse;
import org.transportsgt.customerwalletservice.exception.RespuestaError;
import org.transportsgt.customerwalletservice.services.RepublicacionEventosService;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_SISTEMA')")
@Tag(name = "Administración", description = "Operaciones de mantenimiento (solo ADMIN_SISTEMA)")
public class AdminController {

    private final RepublicacionEventosService republicacionService;

    @PostMapping("/republicar-eventos")
    @Operation(summary = "Republicar perfiles de cliente",
            description = "Publica PerfilClienteActualizado de todos los clientes con su versión actual para "
                    + "reconstruir las proyecciones de Boletos y Alquileres. Es idempotente: los consumidores "
                    + "ignoran las versiones que ya aplicaron.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Eventos guardados en el outbox",
                    content = @Content(schema = @Schema(implementation = RepublicacionResponse.class),
                            examples = @ExampleObject(value = "{\"clientes\":2}"))),
            @ApiResponse(responseCode = "401", description = "Sin token",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_401))),
            @ApiResponse(responseCode = "403", description = "No es ADMIN_SISTEMA",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_403)))
    })
    public RepublicacionResponse republicar() {
        return republicacionService.republicar();
    }
}
