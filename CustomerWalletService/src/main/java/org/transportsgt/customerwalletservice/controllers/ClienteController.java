package org.transportsgt.customerwalletservice.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.transportsgt.customerwalletservice.config.security.authentication.ConvertidorUsuarioAutenticado;
import org.transportsgt.customerwalletservice.dto.request.ActualizarPerfilRequest;
import org.transportsgt.customerwalletservice.dto.response.ClienteBusquedaResponse;
import org.transportsgt.customerwalletservice.dto.response.ClienteResponse;
import org.transportsgt.customerwalletservice.exception.RespuestaError;
import org.transportsgt.customerwalletservice.services.ClienteService;

@RestController
@RequestMapping("/clientes")
@RequiredArgsConstructor
@Tag(name = "Clientes", description = "Perfil comercial (NIT, DPI, teléfono, dirección)")
public class ClienteController {

    private final ConvertidorUsuarioAutenticado convertidorUsuarioAutenticado;

    private final ClienteService clienteService;

    @GetMapping("/yo")
    @Operation(summary = "Mi perfil", description = "Perfil del usuario autenticado y saldo de su cartera.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil",
                    content = @Content(schema = @Schema(implementation = ClienteResponse.class),
                            examples = @ExampleObject(value = EjemplosApi.CLIENTE))),
            @ApiResponse(responseCode = "401", description = "Sin token o token inválido",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_401))),
            @ApiResponse(responseCode = "404", description = "El perfil aún no existe (llega con UsuarioCreado)",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_404_CLIENTE)))
    })
    public ClienteResponse obtenerPerfil(@AuthenticationPrincipal Jwt jwt) {
        return clienteService.obtenerPerfil(convertidorUsuarioAutenticado.desde(jwt));
    }

    @PutMapping("/yo")
    @Operation(summary = "Actualizar mi perfil",
            description = "NIT, DPI, teléfono y dirección (el nombre y el correo vienen de Identidad). El NIT se "
                    + "guarda normalizado y CF se rechaza. Si cambia el NIT publica PerfilClienteActualizado.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    examples = @ExampleObject(value = """
                            {"nit":"1234567-K","dpi":"2456789010101","telefono":"55551234",
                            "direccion":"4a. Calle 12-35, Zona 1, Quetzaltenango"}"""))))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil actualizado",
                    content = @Content(schema = @Schema(implementation = ClienteResponse.class),
                            examples = @ExampleObject(value = EjemplosApi.CLIENTE))),
            @ApiResponse(responseCode = "400", description = "NIT inválido o CF, DPI o teléfono inválidos",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_400_NIT))),
            @ApiResponse(responseCode = "404", description = "El perfil aún no existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_404_CLIENTE))),
            @ApiResponse(responseCode = "409", description = "NIT o DPI ya registrados por otro cliente",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_409_NIT)))
    })
    public ClienteResponse actualizarPerfil(@Valid @RequestBody ActualizarPerfilRequest request,
                                            @AuthenticationPrincipal Jwt jwt) {
        return clienteService.actualizarPerfil(convertidorUsuarioAutenticado.desde(jwt), request);
    }

    @GetMapping("/buscar")
    @PreAuthorize("hasAnyRole('CAJERO', 'ADMIN_SUCURSAL')")
    @Operation(summary = "Buscar cliente por NIT (mostrador)",
            description = "Para enlazar una venta presencial al cliente. El NIT se normaliza antes de buscar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cliente",
                    content = @Content(schema = @Schema(implementation = ClienteBusquedaResponse.class),
                            examples = @ExampleObject(value = EjemplosApi.CLIENTE_BUSQUEDA))),
            @ApiResponse(responseCode = "403", description = "No es CAJERO ni ADMIN_SUCURSAL",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_403))),
            @ApiResponse(responseCode = "404", description = "No hay cliente con ese NIT",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class),
                            examples = @ExampleObject(value = EjemplosApi.ERROR_404_CLIENTE)))
    })
    public ClienteBusquedaResponse buscar(
            @Parameter(description = "NIT (con o sin guion)", example = "1234567-K") @RequestParam String nit) {
        return clienteService.buscarPorNit(nit);
    }
}
