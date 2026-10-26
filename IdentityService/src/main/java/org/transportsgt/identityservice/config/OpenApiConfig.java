package org.transportsgt.identityservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger: {@code /v1/identity/swagger-ui.html}. El servidor es relativo para que "Try it out"
 * funcione igual entrando directo al servicio o por el gateway.
 */
@Configuration
public class OpenApiConfig {

    public static final String ESQUEMA_JWT = "bearerAuth";

    @Bean
    public OpenAPI identidadOpenApi(@Value("${server.servlet.context-path:}") String contextPath) {
        return new OpenAPI()
                .info(new Info()
                        .title("Servicio de Identidad y Administración")
                        .version("v1")
                        .description("Usuarios, roles, sucursales, emisión de JWT y gestión de contraseñas. "
                                + "Inicie sesión en POST /auth/login y use el token con el botón Authorize."))
                .servers(List.of(new Server().url(contextPath.isBlank() ? "/" : contextPath)))
                .components(new Components().addSecuritySchemes(ESQUEMA_JWT, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_JWT));
    }
}
