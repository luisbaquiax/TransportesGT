package org.transportsgt.privaterentalservice.config.security;

import org.transportsgt.privaterentalservice.config.security.handlers.JsonAccessDeniedHandler;
import org.transportsgt.privaterentalservice.config.security.handlers.JsonAuthenticationEntryPoint;

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
 * <p>Rutas relativas al context-path {@code /v1/rentals}.
 * Reglas por rol (coarse-grained en Spring Security; las de negocio, en el servicio):
 * - POST /solicitudes: cualquier usuario autenticado (cliente en línea).
 * - POST /mostrador, /pago-mostrador: SUC o CAJ.
 * - POST /{id}/cotizacion: SUC (la sucursal del alquiler se valida en el servicio).
 * - POST /{id}/pago: el cliente dueño del alquiler (se valida en el servicio).
 * - GET /mios, /{id}: autenticado (quién ve cada alquiler se valida en el servicio).
 * - GET /: SUC, CAJ o SIS (filtro por sucursal en la consulta).
 * - GET /tarifas/vigente: autenticado. GET y POST /tarifas: SIS.
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
                        // Reglas propias de Alquileres
                        // Tarifas antes de /alquileres/* para que GET /alquileres/tarifas no caiga en el detalle
                        .requestMatchers(HttpMethod.GET, "/alquileres/tarifas/vigente")
                        .authenticated()
                        .requestMatchers("/alquileres/tarifas")
                        .hasRole("ADMIN_SISTEMA")
                        .requestMatchers(HttpMethod.POST, "/alquileres/solicitudes")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/alquileres/mostrador",
                                "/alquileres/*/pago-mostrador")
                        .hasAnyRole("ADMIN_SUCURSAL", "CAJERO")
                        .requestMatchers(HttpMethod.POST, "/alquileres/*/cotizacion")
                        .hasRole("ADMIN_SUCURSAL")
                        .requestMatchers(HttpMethod.POST, "/alquileres/*/pago")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/alquileres/mios", "/alquileres/*")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/alquileres")
                        .hasAnyRole("ADMIN_SUCURSAL", "CAJERO", "ADMIN_SISTEMA")
                        //
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
