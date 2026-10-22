package org.transportsgt.fleetservice.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.transportsgt.fleetservice.exception.RespuestaError;
import org.transportsgt.fleetservice.services.FotoService;

import java.util.Locale;
import java.util.Map;

/**
 * Lectura pública de las fotos ({@code /fotos/**} está en RUTAS_PUBLICAS de SecurityConfig):
 * el frontend las muestra en {@code <img src="...">}. La subida va en POST /buses/{id}/foto
 * y POST /choferes/{id}/foto.
 */
@RestController
@RequestMapping("/fotos")
@RequiredArgsConstructor
@Tag(name = "Fotos (obsoleto)", description = "Ruta anterior de lectura de fotos. Ya no es pública: solo "
        + "administradores y sin control por sucursal. Use GET /buses/{id}/foto y GET /choferes/{id}/foto.")
@Deprecated(forRemoval = true)
public class FotosController {

    private static final Map<String, FotoService.Tipo> TIPOS = Map.of(
            "buses", FotoService.Tipo.BUSES,
            "choferes", FotoService.Tipo.CHOFERES);

    private final FotoService fotoService;

    @GetMapping("/{tipo}/{nombre}")
    @Operation(summary = "Obtener una foto",
            description = "Obsoleto: sirve la imagen guardada (JPEG, PNG o WebP) a administradores.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Imagen",
                    content = @Content(mediaType = MediaType.IMAGE_JPEG_VALUE)),
            @ApiResponse(responseCode = "404", description = "No existe o nombre inválido",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public ResponseEntity<Resource> obtener(
            @Parameter(description = "buses o choferes", example = "buses")
            @PathVariable String tipo,
            @Parameter(description = "Nombre del archivo", example = "5d1c0a7e-8b43-4f2e-9a51-2d8f8c0e9a11.jpg")
            @PathVariable String nombre) {
        FotoService.Tipo tipoEnum = TIPOS.get(tipo.toLowerCase(Locale.ROOT));
        if (tipoEnum == null) {
            return ResponseEntity.notFound().build();
        }
        return fotoService.leer(tipoEnum, nombre)
                .map(resource -> ResponseEntity.ok()
                        .contentType(tipado(nombre))
                        .cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofDays(7)).cachePrivate())
                        .body(resource))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static MediaType tipado(String nombre) {
        String bajo = nombre.toLowerCase(Locale.ROOT);
        if (bajo.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (bajo.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_JPEG;
    }
}
