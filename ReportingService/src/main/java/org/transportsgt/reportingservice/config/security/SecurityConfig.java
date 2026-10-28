package org.transportsgt.reportingservice.config.security;

import org.transportsgt.reportingservice.config.security.handlers.JsonAccessDeniedHandler;
import org.transportsgt.reportingservice.config.security.handlers.JsonAuthenticationEntryPoint;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Sin CORS (lo resuelve el gateway) y sin login (lo hace Identidad).
 *
 * <p>Rutas relativas al context-path {@code /v1/reports}.
 * Lo único que cambia entre servicios es el bloque de reglas.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] RUTAS_PUBLICAS = {
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/actuator/health/**"
    };

    /** Reportes 1 a 6 (administrador del sistema). */
    private static final String[] REPORTES_SISTEMA = {
            "/reportes/ingresos",
            "/reportes/gastos",
            "/reportes/ganancias",
            "/reportes/rutas-demandadas",
            "/reportes/gastos-taller-por-categoria",
            "/reportes/mapa-rutas"
    };

    /** Reportes 7 a 12 (administrador de sucursal; el del sistema también puede abrirlos). */
    private static final String[] REPORTES_SUCURSAL = {
            "/reportes/buses",
            "/reportes/choferes",
            "/reportes/ingresos-boletos",
            "/reportes/ingresos-alquileres",
            "/reportes/depreciacion-buses",
            "/reportes/gastos-taller-por-bus"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationConverter jwtAuthenticationConverter,
                                                   JsonAuthenticationEntryPoint entryPoint,
                                                   JsonAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(RUTAS_PUBLICAS).permitAll()
                        // ---- Reglas propias de Reportes (la regla de "su sucursal" se valida en el servicio) ----
                        .requestMatchers(HttpMethod.GET, "/reportes/catalogos/**")
                                .hasAnyRole("ADMIN_SISTEMA", "ADMIN_SUCURSAL")
                        .requestMatchers(HttpMethod.GET, REPORTES_SISTEMA).hasRole("ADMIN_SISTEMA")
                        .requestMatchers(HttpMethod.GET, REPORTES_SUCURSAL)
                                .hasAnyRole("ADMIN_SUCURSAL", "ADMIN_SISTEMA")
                        // ------------------------------------------------------------
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(oauth -> oauth
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

        return http.build();
    }
}
