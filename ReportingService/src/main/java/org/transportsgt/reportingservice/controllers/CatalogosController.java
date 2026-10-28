package org.transportsgt.reportingservice.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.transportsgt.reportingservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.reportingservice.dto.response.catalogos.OpcionCatalogo;
import org.transportsgt.reportingservice.services.catalogos.CatalogoService;

import java.util.List;
import java.util.UUID;

/**
 * Listas para los filtros de los reportes (ADMIN_SISTEMA y ADMIN_SUCURSAL).
 */
@RestController
@RequestMapping("/reportes/catalogos")
@RequiredArgsConstructor
@Tag(name = "Catálogos de reportes", description = "Sucursales, rutas y buses para llenar los filtros")
public class CatalogosController {

    private final CatalogoService catalogoService;

    @GetMapping("/sucursales")
    @PreAuthorize("hasRole('ADMIN_SISTEMA')")
    @Operation(summary = "Sucursales en orden alfabético (solo ADMIN_SISTEMA)")
    public List<OpcionCatalogo> sucursales() {
        return catalogoService.sucursales();
    }

    @GetMapping("/rutas")
    @Operation(summary = "Rutas que salen de la sucursal", description = "El ADMIN_SUCURSAL siempre recibe las de "
            + "su sucursal; el ADMIN_SISTEMA puede omitir idSucursal para recibir todas.")
    public List<OpcionCatalogo> rutas(@AuthenticationPrincipal Jwt jwt,
                                      @RequestParam(required = false) UUID idSucursal) {
        return catalogoService.rutas(UsuarioAutenticado.de(jwt), idSucursal);
    }

    @GetMapping("/buses")
    @Operation(summary = "Buses de la sucursal", description = "El ADMIN_SUCURSAL siempre recibe los de su "
            + "sucursal; el ADMIN_SISTEMA puede omitir idSucursal para recibir todos.")
    public List<OpcionCatalogo> buses(@AuthenticationPrincipal Jwt jwt,
                                      @RequestParam(required = false) UUID idSucursal) {
        return catalogoService.buses(UsuarioAutenticado.de(jwt), idSucursal);
    }
}
