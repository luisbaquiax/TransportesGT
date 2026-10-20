package org.transportsgt.notificationservice.controllers;

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
import org.transportsgt.notificationservice.dto.response.ProcesamientoResponse;
import org.transportsgt.notificationservice.exception.RespuestaError;
import org.transportsgt.notificationservice.services.envio.EnvioCorreosService;
import org.transportsgt.notificationservice.services.licencia.AvisosLicenciaService;

@RestController
@RequestMapping("/notificaciones/interno")
@RequiredArgsConstructor
@Tag(name = "Procesamiento", description = "Ejecución manual de los jobs (pruebas y Cloud Scheduler)")
public class ProcesamientoController {

    private final AvisosLicenciaService avisosLicencia;
    private final EnvioCorreosService envioCorreos;

    @PostMapping("/procesar")
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    @Operation(summary = "Ejecutar los jobs una vez",
            description = "Primero los avisos de licencia del día (encola sus correos) y luego un lote del envío de "
                    + "correos. Es seguro llamarlo mientras corren los jobs programados.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumen de la corrida",
                    content = @Content(examples = @ExampleObject(value = EjemplosApi.PROCESAMIENTO))),
            @ApiResponse(responseCode = "403", description = "No es ADMIN_SISTEMA",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_403)))
    })
    public ProcesamientoResponse procesar() {
        return new ProcesamientoResponse(avisosLicencia.procesar(), envioCorreos.procesar());
    }
}
