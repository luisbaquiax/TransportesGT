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
import org.springframework.web.bind.annotation.RestController;
import org.transportsgt.ticketservice.config.security.authentication.ConvertidorUsuarioAutenticado;
import org.transportsgt.ticketservice.dto.request.AbordajeRequest;
import org.transportsgt.ticketservice.dto.response.AbordajeResponse;
import org.transportsgt.ticketservice.dto.response.PasajerosViajeResponse;
import org.transportsgt.ticketservice.dto.response.Resultado;
import org.transportsgt.ticketservice.exception.RespuestaError;
import org.transportsgt.ticketservice.services.abordaje.AbordajeService;
import org.transportsgt.ticketservice.services.abordaje.PasajerosViajeService;

import java.util.UUID;

@RestController
@RequestMapping("/boletos/viajes/{idViaje}")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('CAJERO', 'ADMIN_SUCURSAL')")
@Tag(name = "Abordaje", description = "Lista de pasajeros y registro del abordaje (SUC y CAJ de la sucursal de origen)")
public class AbordajeController {

    private final ConvertidorUsuarioAutenticado convertidorUsuarioAutenticado;

    private final PasajerosViajeService pasajerosViaje;
    private final AbordajeService abordaje;

    @GetMapping("/pasajeros")
    @Operation(summary = "Lista de pasajeros",
            description = "Boletos CONFIRMADO del viaje por asiento, con la hora de abordaje (null si aún no abordó).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pasajeros",
                    content = @Content(schema = @Schema(implementation = PasajerosViajeResponse.class),
                            examples = @ExampleObject(value = EjemplosApi.PASAJEROS))),
            @ApiResponse(responseCode = "403", description = "El viaje sale de otra sucursal",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_403_SUCURSAL))),
            @ApiResponse(responseCode = "404", description = "El viaje no existe o se eliminó",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_404_VIAJE)))
    })
    public PasajerosViajeResponse pasajeros(@PathVariable UUID idViaje, @AuthenticationPrincipal Jwt jwt) {
        return pasajerosViaje.pasajeros(convertidorUsuarioAutenticado.desde(jwt), idViaje);
    }

    @PostMapping("/abordajes")
    @Operation(summary = "Registrar abordaje",
            description = "Con el código del boleto (QR o a mano). El viaje debe estar PROGRAMADO y el boleto CONFIRMADO y "
                    + "de ese viaje. Repetirlo no falla: responde 200 con el abordaje original.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    examples = @ExampleObject(value = "{\"codigo\":\"BOL-7K3MPQ2A\"}"))))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Abordaje registrado",
                    content = @Content(schema = @Schema(implementation = AbordajeResponse.class),
                            examples = @ExampleObject(value = EjemplosApi.ABORDAJE))),
            @ApiResponse(responseCode = "200", description = "El pasajero ya había abordado"),
            @ApiResponse(responseCode = "403", description = "El viaje sale de otra sucursal",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_403_SUCURSAL))),
            @ApiResponse(responseCode = "404", description = "El viaje o el boleto no existen",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_404_BOLETO))),
            @ApiResponse(responseCode = "409", description = "Boleto anulado o de otro viaje, o el viaje ya salió",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = {@ExampleObject(name = "BOLETO_NO_VALIDO", value = EjemplosApi.ERROR_409_BOLETO),
                                    @ExampleObject(name = "VIAJE_NO_DISPONIBLE", value = EjemplosApi.ERROR_409_VIAJE)}))
    })
    public ResponseEntity<AbordajeResponse> abordar(@PathVariable UUID idViaje,
                                                    @Valid @RequestBody AbordajeRequest request,
                                                    @AuthenticationPrincipal Jwt jwt) {
        Resultado<AbordajeResponse> resultado =
                abordaje.registrar(convertidorUsuarioAutenticado.desde(jwt), idViaje, request.codigo());
        return ResponseEntity.status(resultado.creado() ? HttpStatus.CREATED : HttpStatus.OK).body(resultado.valor());
    }
}
