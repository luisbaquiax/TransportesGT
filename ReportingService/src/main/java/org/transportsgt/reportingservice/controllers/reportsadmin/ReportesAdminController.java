package org.transportsgt.reportingservice.controllers.reportsadmin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.transportsgt.reportingservice.dto.request.FiltroFechas;
import org.transportsgt.reportingservice.dto.request.FormatoReporte;
import org.transportsgt.reportingservice.services.comun.RespuestaReporte;
import org.transportsgt.reportingservice.services.comun.ValidadorFiltros;
import org.transportsgt.reportingservice.services.reportsadmin.ReporteGananciasService;
import org.transportsgt.reportingservice.services.reportsadmin.ReporteGastosService;
import org.transportsgt.reportingservice.services.reportsadmin.ReporteGastosTallerCategoriaService;
import org.transportsgt.reportingservice.services.reportsadmin.ReporteIngresosService;
import org.transportsgt.reportingservice.services.reportsadmin.ReporteMapaRutasService;
import org.transportsgt.reportingservice.services.reportsadmin.ReporteRutasDemandadasService;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Reportes 1 a 6 (solo ADMIN_SISTEMA, regla en SecurityConfig). Todos aceptan {@code desde}/{@code hasta}
 * (fecha de negocio, inclusivos; sin intervalo = todos los registros) y {@code formato=json|pdf}.
 */
@RestController
@RequestMapping("/reportes")
@RequiredArgsConstructor
@Tag(name = "Reportes del sistema", description = "Ingresos, gastos, ganancias, rutas, taller y mapa (ADMIN_SISTEMA)")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Reporte en JSON o PDF"),
        @ApiResponse(responseCode = "400", description = "desde > hasta, formato no soportado o parámetro inválido"),
        @ApiResponse(responseCode = "401", description = "Sin token"),
        @ApiResponse(responseCode = "403", description = "No es ADMIN_SISTEMA"),
        @ApiResponse(responseCode = "404", description = "Sucursal o bus inexistente")
})
public class ReportesAdminController {

    private final ValidadorFiltros validador;
    private final RespuestaReporte respuesta;
    private final ReporteIngresosService ingresosService;
    private final ReporteGastosService gastosService;
    private final ReporteGananciasService gananciasService;
    private final ReporteRutasDemandadasService rutasDemandadasService;
    private final ReporteGastosTallerCategoriaService tallerCategoriaService;
    private final ReporteMapaRutasService mapaRutasService;

    @GetMapping("/ingresos")
    @Operation(summary = "1. Ingresos por sucursal", description = "Listado de ingresos (boletos y alquileres no "
            + "anulados) por sucursal, subtotal por sucursal y total global. Filtra por fecha de pago.")
    public ResponseEntity<?> ingresos(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                      @RequestParam(required = false) UUID idSucursal,
                                      @Parameter(description = "json | pdf") @RequestParam(required = false) String formato) {
        FiltroFechas intervalo = validador.intervalo(desde, hasta);
        FormatoReporte salida = validador.formato(formato);
        return respuesta.responder(ingresosService.generar(intervalo, idSucursal), salida,
                ReporteIngresosService.NOMBRE);
    }

    @GetMapping("/gastos")
    @Operation(summary = "2. Gastos por sucursal", description = "Combustible, taller (mano de obra y repuesto), "
            + "depreciación y salario del chofer por sucursal, con desglose por tipo. Filtra por fecha del gasto.")
    public ResponseEntity<?> gastos(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                    @RequestParam(required = false) UUID idSucursal,
                                    @Parameter(description = "json | pdf") @RequestParam(required = false) String formato) {
        FiltroFechas intervalo = validador.intervalo(desde, hasta);
        FormatoReporte salida = validador.formato(formato);
        return respuesta.responder(gastosService.generar(intervalo, idSucursal), salida,
                ReporteGastosService.NOMBRE);
    }

    @GetMapping("/ganancias")
    @Operation(summary = "3. Ganancias por sucursal", description = "Ingresos, gastos y ganancia neta por sucursal "
            + "(orden alfabético, incluye sucursales en cero) con totales.")
    public ResponseEntity<?> ganancias(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                       @RequestParam(required = false) UUID idSucursal,
                                       @Parameter(description = "json | pdf") @RequestParam(required = false) String formato) {
        FiltroFechas intervalo = validador.intervalo(desde, hasta);
        FormatoReporte salida = validador.formato(formato);
        return respuesta.responder(gananciasService.generar(intervalo, idSucursal), salida,
                ReporteGananciasService.NOMBRE);
    }

    @GetMapping("/rutas-demandadas")
    @Operation(summary = "4. Rutas más demandadas", description = "Rutas de mayor a menor por boletos vendidos, "
            + "con el total de boletos. Incluye rutas eliminadas con ventas en el intervalo.")
    public ResponseEntity<?> rutasDemandadas(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                             @Parameter(description = "json | pdf") @RequestParam(required = false) String formato) {
        FiltroFechas intervalo = validador.intervalo(desde, hasta);
        FormatoReporte salida = validador.formato(formato);
        return respuesta.responder(rutasDemandadasService.generar(intervalo), salida,
                ReporteRutasDemandadasService.NOMBRE);
    }

    @GetMapping("/gastos-taller-por-categoria")
    @Operation(summary = "5. Gastos de taller por categoría", description = "Detalle y total por categoría (de "
            + "mayor a menor) y gran total. Filtros opcionales por sucursal y bus.")
    public ResponseEntity<?> gastosTallerPorCategoria(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                                      @RequestParam(required = false) UUID idSucursal,
                                                      @RequestParam(required = false) UUID idBus,
                                                      @Parameter(description = "json | pdf") @RequestParam(required = false) String formato) {
        FiltroFechas intervalo = validador.intervalo(desde, hasta);
        FormatoReporte salida = validador.formato(formato);
        return respuesta.responder(tallerCategoriaService.generar(intervalo, idSucursal, idBus),
                salida, ReporteGastosTallerCategoriaService.NOMBRE);
    }

    @GetMapping("/mapa-rutas")
    @Operation(summary = "6. Mapa de rutas", description = "Rutas vigentes con origen en la sucursal, con "
            + "coordenadas de origen y destino (Leaflet). idSucursal es obligatorio.")
    public ResponseEntity<?> mapaRutas(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                       @RequestParam(required = false) UUID idSucursal,
                                       @Parameter(description = "json | pdf") @RequestParam(required = false) String formato) {
        FiltroFechas intervalo = validador.intervalo(desde, hasta);
        FormatoReporte salida = validador.formato(formato);
        return respuesta.responder(mapaRutasService.generar(intervalo, idSucursal), salida,
                ReporteMapaRutasService.NOMBRE);
    }
}
