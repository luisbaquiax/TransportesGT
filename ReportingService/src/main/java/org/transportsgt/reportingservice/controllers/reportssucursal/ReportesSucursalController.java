package org.transportsgt.reportingservice.controllers.reportssucursal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.transportsgt.eventos.payload.EstadoOperativo;
import org.transportsgt.reportingservice.config.security.UsuarioAutenticado;
import org.transportsgt.reportingservice.dto.request.FiltroFechas;
import org.transportsgt.reportingservice.dto.request.FormatoReporte;
import org.transportsgt.reportingservice.services.comun.RespuestaReporte;
import org.transportsgt.reportingservice.services.comun.ValidadorFiltros;
import org.transportsgt.reportingservice.services.reportssucursal.ReporteBusesService;
import org.transportsgt.reportingservice.services.reportssucursal.ReporteChoferesService;
import org.transportsgt.reportingservice.services.reportssucursal.ReporteDepreciacionBusesService;
import org.transportsgt.reportingservice.services.reportssucursal.ReporteGastosTallerBusService;
import org.transportsgt.reportingservice.services.reportssucursal.ReporteIngresosAlquileresService;
import org.transportsgt.reportingservice.services.reportssucursal.ReporteIngresosBoletosService;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Reportes 7 a 12 (ADMIN_SUCURSAL y ADMIN_SISTEMA, regla en SecurityConfig). El ADMIN_SUCURSAL siempre ve su
 * sucursal (403 si pide otra); el ADMIN_SISTEMA indica {@code idSucursal} (obligatorio).
 */
@RestController
@RequestMapping("/reportes")
@RequiredArgsConstructor
@Tag(name = "Reportes de sucursal", description = "Buses, choferes, ingresos, depreciación y taller de una sucursal")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Reporte en JSON o PDF"),
        @ApiResponse(responseCode = "400", description = "desde > hasta, formato no soportado o falta idSucursal (ADMIN_SISTEMA)"),
        @ApiResponse(responseCode = "401", description = "Sin token"),
        @ApiResponse(responseCode = "403", description = "Rol sin acceso, o sucursal, bus o ruta de otra sucursal"),
        @ApiResponse(responseCode = "404", description = "Sucursal, bus o ruta inexistente")
})
public class ReportesSucursalController {

    private final ValidadorFiltros validador;
    private final RespuestaReporte respuesta;
    private final ReporteBusesService busesService;
    private final ReporteChoferesService choferesService;
    private final ReporteIngresosBoletosService ingresosBoletosService;
    private final ReporteIngresosAlquileresService ingresosAlquileresService;
    private final ReporteDepreciacionBusesService depreciacionService;
    private final ReporteGastosTallerBusService tallerBusService;

    @GetMapping("/buses")
    @Operation(summary = "7. Listado de buses", description = "Placa, marca, modelo, capacidad, estado, chofer "
            + "actual, kilometraje y viajes realizados. Filtro opcional por estado operativo.")
    public ResponseEntity<?> buses(@AuthenticationPrincipal Jwt jwt,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                   @RequestParam(required = false) UUID idSucursal,
                                   @RequestParam(required = false) EstadoOperativo estado,
                                   @Parameter(description = "json | pdf") @RequestParam(required = false) String formato) {
        FiltroFechas intervalo = validador.intervalo(desde, hasta);
        FormatoReporte salida = validador.formato(formato);
        return respuesta.responder(busesService.generar(UsuarioAutenticado.de(jwt), idSucursal, intervalo, estado),
                salida, ReporteBusesService.NOMBRE);
    }

    @GetMapping("/choferes")
    @Operation(summary = "8. Listado de choferes", description = "Licencia, nombre, tipo, vencimiento (vencidas o "
            + "por vencer en 30 días), estado y viajes realizados.")
    public ResponseEntity<?> choferes(@AuthenticationPrincipal Jwt jwt,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                      @RequestParam(required = false) UUID idSucursal,
                                      @Parameter(description = "json | pdf") @RequestParam(required = false) String formato) {
        FiltroFechas intervalo = validador.intervalo(desde, hasta);
        FormatoReporte salida = validador.formato(formato);
        return respuesta.responder(choferesService.generar(UsuarioAutenticado.de(jwt), idSucursal, intervalo),
                salida, ReporteChoferesService.NOMBRE);
    }

    @GetMapping("/ingresos-boletos")
    @Operation(summary = "9. Ingresos por venta de boletos", description = "Por viaje: ruta, salida, bus, boletos e "
            + "ingreso. Filtros opcionales por ruta y por bus. Filtra por fecha de pago.")
    public ResponseEntity<?> ingresosBoletos(@AuthenticationPrincipal Jwt jwt,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                             @RequestParam(required = false) UUID idSucursal,
                                             @RequestParam(required = false) UUID idRuta,
                                             @RequestParam(required = false) UUID idBus,
                                             @Parameter(description = "json | pdf") @RequestParam(required = false) String formato) {
        FiltroFechas intervalo = validador.intervalo(desde, hasta);
        FormatoReporte salida = validador.formato(formato);
        return respuesta.responder(ingresosBoletosService.generar(UsuarioAutenticado.de(jwt), idSucursal, intervalo,
                idRuta, idBus), salida, ReporteIngresosBoletosService.NOMBRE);
    }

    @GetMapping("/ingresos-alquileres")
    @Operation(summary = "10. Ingresos por alquiler de buses", description = "Cliente, origen, destino, fechas, bus "
            + "asignado, fecha de pago y precio total. Filtra por fecha de pago.")
    public ResponseEntity<?> ingresosAlquileres(@AuthenticationPrincipal Jwt jwt,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                                @RequestParam(required = false) UUID idSucursal,
                                                @Parameter(description = "json | pdf") @RequestParam(required = false) String formato) {
        FiltroFechas intervalo = validador.intervalo(desde, hasta);
        FormatoReporte salida = validador.formato(formato);
        return respuesta.responder(ingresosAlquileresService.generar(UsuarioAutenticado.de(jwt), idSucursal,
                intervalo), salida, ReporteIngresosAlquileresService.NOMBRE);
    }

    @GetMapping("/depreciacion-buses")
    @Operation(summary = "11. Depreciación por bus", description = "Km recorridos, tarifa vigente, depreciación "
            + "acumulada y promedio aplicado por km. Filtra por fecha de cálculo.")
    public ResponseEntity<?> depreciacionBuses(@AuthenticationPrincipal Jwt jwt,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                               @RequestParam(required = false) UUID idSucursal,
                                               @Parameter(description = "json | pdf") @RequestParam(required = false) String formato) {
        FiltroFechas intervalo = validador.intervalo(desde, hasta);
        FormatoReporte salida = validador.formato(formato);
        return respuesta.responder(depreciacionService.generar(UsuarioAutenticado.de(jwt), idSucursal, intervalo),
                salida, ReporteDepreciacionBusesService.NOMBRE);
    }

    @GetMapping("/gastos-taller-por-bus")
    @Operation(summary = "12. Gastos de taller por bus y categoría", description = "Por bus (de mayor a menor gasto),"
            + " las líneas agrupadas por categoría con subtotal y total del bus. Filtro opcional por bus.")
    public ResponseEntity<?> gastosTallerPorBus(@AuthenticationPrincipal Jwt jwt,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                                @RequestParam(required = false) UUID idSucursal,
                                                @RequestParam(required = false) UUID idBus,
                                                @Parameter(description = "json | pdf") @RequestParam(required = false) String formato) {
        FiltroFechas intervalo = validador.intervalo(desde, hasta);
        FormatoReporte salida = validador.formato(formato);
        return respuesta.responder(tallerBusService.generar(UsuarioAutenticado.de(jwt), idSucursal, intervalo, idBus),
                salida, ReporteGastosTallerBusService.NOMBRE);
    }
}
