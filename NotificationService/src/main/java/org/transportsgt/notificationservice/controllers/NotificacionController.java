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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.transportsgt.notificationservice.dto.response.NotificacionDetalleResponse;
import org.transportsgt.notificationservice.dto.response.NotificacionResumenResponse;
import org.transportsgt.notificationservice.dto.response.PaginaResponse;
import org.transportsgt.notificationservice.exception.RespuestaError;
import org.transportsgt.notificationservice.models.EstadoNotificacion;
import org.transportsgt.notificationservice.models.TipoNotificacion;
import org.transportsgt.notificationservice.services.bitacora.BitacoraService;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/notificaciones")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_SISTEMA')")
@Tag(name = "Bitácora de correos", description = "Consulta y reintento de los correos (solo ADMIN_SISTEMA)")
public class NotificacionController {

    private final BitacoraService bitacora;

    @GetMapping
    @Operation(summary = "Bitácora de correos",
            description = "Más recientes primero. No incluye el cuerpo del correo: use el detalle. desde y hasta son "
                    + "días de creación en hora de Guatemala (ambos inclusivos).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de notificaciones",
                    content = @Content(examples = @ExampleObject(value = EjemplosApi.BITACORA))),
            @ApiResponse(responseCode = "400", description = "Rango de fechas o paginación inválidos",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_400))),
            @ApiResponse(responseCode = "401", description = "Sin token o token inválido",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_401))),
            @ApiResponse(responseCode = "403", description = "No es ADMIN_SISTEMA",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_403)))
    })
    public PaginaResponse<NotificacionResumenResponse> listar(
            @Parameter(description = "PENDIENTE, ENVIADA o FALLIDA") @RequestParam(required = false) EstadoNotificacion estado,
            @Parameter(description = "Tipo de correo, p. ej. COMPRA_BOLETOS") @RequestParam(required = false) TipoNotificacion tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return bitacora.listar(estado, tipo, desde, hasta, pagina, tamano);
    }

    @GetMapping("/{idNotificacion}")
    @Operation(summary = "Detalle de un correo", description = "Incluye el cuerpo HTML. Para ENLACE_CONTRASENA el cuerpo "
            + "es el texto fijo \"[contenido sensible no almacenado]\".")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notificación",
                    content = @Content(examples = @ExampleObject(value = EjemplosApi.DETALLE))),
            @ApiResponse(responseCode = "404", description = "No existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_404)))
    })
    public NotificacionDetalleResponse detalle(@PathVariable UUID idNotificacion) {
        return bitacora.detalle(idNotificacion);
    }

    @PostMapping("/{idNotificacion}/reintentar")
    @Operation(summary = "Reintentar un correo FALLIDA",
            description = "Lo deja PENDIENTE con 0 intentos; el job de envío lo manda en los próximos 30 s. Un "
                    + "ENLACE_CONTRASENA no se reintenta: el usuario debe pedir un enlace nuevo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Correo reencolado",
                    content = @Content(examples = @ExampleObject(value = EjemplosApi.RESUMEN))),
            @ApiResponse(responseCode = "404", description = "No existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_404))),
            @ApiResponse(responseCode = "409", description = "No está FALLIDA o es un enlace de contraseña",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = {@ExampleObject(name = "Estado", value = EjemplosApi.ERROR_409_ESTADO),
                                    @ExampleObject(name = "Enlace", value = EjemplosApi.ERROR_409_ENLACE)}))
    })
    public NotificacionResumenResponse reintentar(@PathVariable UUID idNotificacion) {
        return bitacora.reintentar(idNotificacion);
    }
}
