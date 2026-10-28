package org.transportsgt.ticketservice.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.transportsgt.ticketservice.config.security.filters.FiltroClaveInterna;
import org.transportsgt.ticketservice.dto.response.AsientoMaximoResponse;
import org.transportsgt.ticketservice.dto.response.MapaAsientosResponse;
import org.transportsgt.ticketservice.dto.response.ViajeDisponibleResponse;
import org.transportsgt.ticketservice.exception.RespuestaError;
import org.transportsgt.ticketservice.services.consulta.ConsultaViajesService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/boletos/viajes")
@RequiredArgsConstructor
@Tag(name = "Viajes", description = "Viajes regulares a la venta y sus asientos")
public class ViajeController {

    private final ConsultaViajesService consultaViajes;

    @GetMapping
    @Operation(summary = "Viajes a la venta",
            description = "Viajes PROGRAMADO, con salida futura y bus asignado, ordenados por hora de salida. Incluye los "
                    + "asientos disponibles. El catálogo de eventos no trae el id de la sucursal destino: se filtra por "
                    + "nombre (destino) o por ruta (idRuta).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Viajes",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ViajeDisponibleResponse.class)),
                            examples = @ExampleObject(value = EjemplosApi.VIAJES))),
            @ApiResponse(responseCode = "401", description = "Sin token o token inválido",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_401)))
    })
    public List<ViajeDisponibleResponse> aLaVenta(
            @Parameter(description = "Sucursal de origen") @RequestParam(required = false) UUID idOrigen,
            @Parameter(description = "Ruta") @RequestParam(required = false) UUID idRuta,
            @Parameter(description = "Parte del nombre del destino", example = "Guatemala")
            @RequestParam(required = false) String destino,
            @Parameter(description = "Día de salida (hora de Guatemala)", example = "2026-10-25")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return consultaViajes.aLaVenta(idOrigen, idRuta, destino, fecha);
    }

    @GetMapping("/{idViaje}/asientos")
    @Operation(summary = "Mapa de asientos", description = "Capacidad del bus y asientos RESERVADO o CONFIRMADO.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mapa de asientos",
                    content = @Content(schema = @Schema(implementation = MapaAsientosResponse.class),
                            examples = @ExampleObject(value = EjemplosApi.MAPA_ASIENTOS))),
            @ApiResponse(responseCode = "404", description = "El viaje no existe o se eliminó",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_404_VIAJE)))
    })
    public MapaAsientosResponse mapaAsientos(@PathVariable UUID idViaje) {
        return consultaViajes.mapaAsientos(idViaje);
    }

    @GetMapping("/{idViaje}/asiento-maximo")
    @Operation(summary = "Mayor asiento vendido (Interno: Viajes)",
            description = "Viajes lo consulta antes de cambiar el bus de un viaje: rechaza un bus con menos asientos. "
                    + "0 si no hay boletos. Acepta el JWT reenviado o la cabecera X-Clave-Interna.",
            parameters = @Parameter(in = ParameterIn.HEADER, name = FiltroClaveInterna.CABECERA, required = false,
                    description = "Secreto compartido de las llamadas internas (alternativa al JWT)",
                    schema = @Schema(type = "string")))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Asiento máximo",
                    content = @Content(schema = @Schema(implementation = AsientoMaximoResponse.class),
                            examples = @ExampleObject(value = EjemplosApi.ASIENTO_MAXIMO))),
            @ApiResponse(responseCode = "401", description = "Sin token, o X-Clave-Interna incorrecta",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_401)))
    })
    public AsientoMaximoResponse asientoMaximo(@PathVariable UUID idViaje) {
        return consultaViajes.asientoMaximo(idViaje);
    }
}
