package org.transportsgt.notificationservice.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.transportsgt.notificationservice.config.security.authentication.ConvertidorUsuarioAutenticado;
import org.transportsgt.notificationservice.dto.response.PaginaResponse;
import org.transportsgt.notificationservice.dto.response.RecordatorioLicenciaResponse;
import org.transportsgt.notificationservice.exception.RespuestaError;
import org.transportsgt.notificationservice.models.EstadoRecordatorio;
import org.transportsgt.notificationservice.services.bitacora.ConsultaLicenciasService;

import java.util.UUID;

@RestController
@RequestMapping("/notificaciones/licencias")
@RequiredArgsConstructor
@Tag(name = "Avisos de licencia", description = "Avisos de vencimiento programados (30, 15 y 3 días antes)")
public class LicenciaController {

    private final ConvertidorUsuarioAutenticado convertidorUsuarioAutenticado;

    private final ConsultaLicenciasService consultaLicencias;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_SISTEMA', 'ADMIN_SUCURSAL')")
    @Operation(summary = "Avisos de licencia programados",
            description = "Ordenados por fecha de envío. ADMIN_SUCURSAL solo ve su sucursal (si pide otra → 403).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de avisos",
                    content = @Content(examples = @ExampleObject(value = EjemplosApi.LICENCIAS))),
            @ApiResponse(responseCode = "403", description = "Otra sucursal o rol sin permiso",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_403_SUCURSAL)))
    })
    public PaginaResponse<RecordatorioLicenciaResponse> listar(
            @Parameter(description = "Sucursal del chofer") @RequestParam(required = false) UUID idSucursal,
            @Parameter(description = "PENDIENTE, ENVIADO, CANCELADO o FALLIDO") @RequestParam(required = false) EstadoRecordatorio estado,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano,
            @AuthenticationPrincipal Jwt jwt) {
        return consultaLicencias.listar(convertidorUsuarioAutenticado.desde(jwt), idSucursal, estado, pagina, tamano);
    }
}
