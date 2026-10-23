package org.transportsgt.operationalcostsservice.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.transportsgt.operationalcostsservice.config.OpenApiConfig;
import org.transportsgt.operationalcostsservice.config.security.UsuarioAutenticado;
import org.transportsgt.operationalcostsservice.dto.request.CategoriaActivaRequest;
import org.transportsgt.operationalcostsservice.dto.request.CategoriaRequest;
import org.transportsgt.operationalcostsservice.dto.request.GastoTallerRequest;
import org.transportsgt.operationalcostsservice.dto.request.TarifaDepreciacionRequest;
import org.transportsgt.operationalcostsservice.dto.response.CategoriaResponse;
import org.transportsgt.operationalcostsservice.dto.response.GastoTallerResponse;
import org.transportsgt.operationalcostsservice.dto.response.PaginaResponse;
import org.transportsgt.operationalcostsservice.dto.response.ResumenCostosViajeResponse;
import org.transportsgt.operationalcostsservice.dto.response.TarifaDepreciacionResponse;
import org.transportsgt.operationalcostsservice.exception.RespuestaError;
import org.transportsgt.operationalcostsservice.services.CategoriaGastoService;
import org.transportsgt.operationalcostsservice.services.GastoTallerService;
import org.transportsgt.operationalcostsservice.services.ResumenViajeService;
import org.transportsgt.operationalcostsservice.services.TarifaDepreciacionService;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/costos")
@RequiredArgsConstructor
@Tag(name = "Costos", description = "Costos operativos: depreciación, categorías, taller y costos de viaje")
public class CostosController {

    /** Límite máximo de elementos por página en listados. */
    private static final int TAMANO_MAXIMO_PAGINA = 100;

    private final TarifaDepreciacionService tarifaService;
    private final CategoriaGastoService categoriaService;
    private final GastoTallerService tallerService;
    private final ResumenViajeService resumenViajeService;

    // ---------- Depreciación ----------

    @GetMapping("/depreciacion/tarifa-vigente")
    @Operation(summary = "Tarifa de depreciación vigente",
            description = "Monto por km aplicable a una fecha (hoy por defecto).")
    public TarifaDepreciacionResponse tarifaVigente(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return tarifaService.tarifaVigente(fecha);
    }

    @GetMapping("/depreciacion/tarifas")
    @Operation(summary = "Historial de tarifas (ADMIN_SISTEMA)")
    @SecurityRequirement(name = OpenApiConfig.ESQUEMA_JWT)
    public List<TarifaDepreciacionResponse> historialTarifas() {
        return tarifaService.historialTarifas();
    }

    @PostMapping("/depreciacion/tarifas")
    @Operation(summary = "Cambiar tarifa (ADMIN_SISTEMA)",
            description = "No altera lo ya calculado. Evento: TarifaDepreciacionCambiada.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tarifa creada",
                    content = @Content(schema = @Schema(implementation = TarifaDepreciacionResponse.class))),
            @ApiResponse(responseCode = "403", description = "No es ADMIN_SISTEMA",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = "{\"codigo\":\"OPERACION_NO_AUTORIZADA\"}")))
    })
    public ResponseEntity<TarifaDepreciacionResponse> cambiarTarifa(
            @Valid @RequestBody TarifaDepreciacionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        TarifaDepreciacionResponse resp = tarifaService.cambiarTarifa(request, UsuarioAutenticado.de(jwt));
        return ResponseEntity.created(URI.create("/costos/depreciacion/tarifas")).body(resp);
    }

    // ---------- Categorías ----------

    @GetMapping("/categorias")
    @Operation(summary = "Catálogo de categorías de gasto")
    public List<CategoriaResponse> listarCategorias(@RequestParam(required = false) Boolean activa) {
        return categoriaService.listarCategorias(activa);
    }

    @PostMapping("/categorias")
    @Operation(summary = "Crear categoría")
    public ResponseEntity<CategoriaResponse> crearCategoria(@Valid @RequestBody CategoriaRequest request) {
        CategoriaResponse resp = categoriaService.crearCategoria(request);
        return ResponseEntity.created(URI.create("/costos/categorias/" + resp.id())).body(resp);
    }

    @PutMapping("/categorias/{id}")
    @Operation(summary = "Actualizar nombre y descripción")
    public CategoriaResponse actualizarCategoria(@PathVariable UUID id,
                                                 @Valid @RequestBody CategoriaRequest request) {
        return categoriaService.actualizarCategoria(id, request);
    }

    @PatchMapping("/categorias/{id}/activa")
    @Operation(summary = "Activar o desactivar categoría")
    public CategoriaResponse cambiarActiva(@PathVariable UUID id,
                                           @Valid @RequestBody CategoriaActivaRequest request) {
        return categoriaService.cambiarActivaCategoria(id, request);
    }

    @DeleteMapping("/categorias/{id}")
    @Operation(summary = "Eliminar categoría",
            description = "Solo sin gastos asociados; si no, 409 CATEGORIA_CON_GASTOS (use desactivar).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Eliminada"),
            @ApiResponse(responseCode = "409", description = "Tiene gastos asociados",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = "{\"codigo\":\"CATEGORIA_CON_GASTOS\"}")))
    })
    public ResponseEntity<Void> eliminarCategoria(@PathVariable UUID id) {
        categoriaService.eliminarCategoria(id);
        return ResponseEntity.noContent().build();
    }

    // ---------- Taller ----------

    @PostMapping("/taller")
    @Operation(summary = "Registrar gasto de taller (ADMIN_SUCURSAL)",
            description = "El total se calcula de las líneas. Inmutable. Evento: GastoTallerRegistrado.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Gasto registrado",
                    content = @Content(schema = @Schema(implementation = GastoTallerResponse.class))),
            @ApiResponse(responseCode = "403", description = "Bus de otra sucursal",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = "{\"codigo\":\"OPERACION_NO_AUTORIZADA\"}")))
    })
    public ResponseEntity<GastoTallerResponse> registrarTaller(
            @Valid @RequestBody GastoTallerRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        GastoTallerResponse resp = tallerService.registrarGastoTaller(request, UsuarioAutenticado.de(jwt));
        return ResponseEntity.created(URI.create("/costos/taller/" + resp.id())).body(resp);
    }

    @GetMapping("/taller")
    @Operation(summary = "Listar gastos de taller",
            description = "SUC ve los de su sucursal; SIS ve todos. Filtros: idBus, desde, hasta.")
    public PaginaResponse<GastoTallerResponse> listarTaller(
            @RequestParam(required = false) UUID idBus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano,
            @AuthenticationPrincipal Jwt jwt) {
        Page<GastoTallerResponse> page = tallerService.listarGastosTaller(idBus, desde, hasta,
                UsuarioAutenticado.de(jwt),
                PageRequest.of(pagina, Math.min(tamano, TAMANO_MAXIMO_PAGINA),
                        Sort.by(Sort.Direction.DESC, "fechaServicio")));
        return new PaginaResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    @GetMapping("/taller/{id}")
    @Operation(summary = "Detalle de gasto de taller con líneas")
    public GastoTallerResponse detalleTaller(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return tallerService.detalleGastoTaller(id, UsuarioAutenticado.de(jwt));
    }

    // ---------- Viaje ----------

    @GetMapping("/viajes/{idViaje}")
    @Operation(summary = "Resumen de costos de un viaje",
            description = "Combustible, depreciación y salario del chofer (generados al consumir LlegadaRegistrada).")
    public ResumenCostosViajeResponse resumenViaje(@PathVariable UUID idViaje,
                                                   @AuthenticationPrincipal Jwt jwt) {
        return resumenViajeService.resumenViaje(idViaje, UsuarioAutenticado.de(jwt));
    }
}
