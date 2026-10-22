package org.transportsgt.tripservice.config.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
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
 * <p>Rutas relativas al context-path {@code /v1/trips}.
 * Reglas por rol (coarse-grained en Spring Security; las de negocio, en el servicio):
 * - GET /rutas: autenticado. POST/PUT/DELETE /rutas: SUC (la sucursal se valida en el servicio).
 * - GET /viajes: SIS, SUC, CAJ, CHO (filtros por sucursal/chofer en el servicio).
 * - POST /viajes, PUT /viajes/id, PATCH /viajes/id/asignacion, DELETE /viajes/id: SUC.
 * - POST /viajes/id/salida y /llegada: SUC o CHO (el chofer solo las suyas, en el servicio).
 * - GET /viajes/activos: interno (Flota), con X-Clave-Interna.
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
    public FilterRegistrationBean<FiltroClaveInterna> filtroClaveInternaSoloSeguridad(
            FiltroClaveInterna filtro) {
        // Va dentro de la cadena de seguridad, no como filtro del servlet.
        FilterRegistrationBean<FiltroClaveInterna> bean = new FilterRegistrationBean<>(filtro);
        bean.setEnabled(false);
        return bean;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationConverter jwtAuthenticationConverter,
                                                   JsonAuthenticationEntryPoint entryPoint,
                                                   JsonAccessDeniedHandler accessDeniedHandler,
                                                   FiltroClaveInterna filtroClaveInterna) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(filtroClaveInterna,
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(RUTAS_PUBLICAS).permitAll()
                        // ---- Reglas propias de Viajes ----
                        .requestMatchers(HttpMethod.GET, "/viajes/activos")
                                .hasRole("INTERNO")
                        .requestMatchers(HttpMethod.GET, "/rutas", "/rutas/*")
                                .authenticated()
                        .requestMatchers(HttpMethod.POST, "/rutas")
                                .hasRole("ADMIN_SUCURSAL")
                        .requestMatchers(HttpMethod.PUT, "/rutas/*")
                                .hasRole("ADMIN_SUCURSAL")
                        .requestMatchers(HttpMethod.DELETE, "/rutas/*")
                                .hasRole("ADMIN_SUCURSAL")
                        // Resumen público para el historial de compras: antes de /viajes/*
                        .requestMatchers(HttpMethod.GET, "/viajes/resumen")
                                .authenticated()
                        .requestMatchers(HttpMethod.GET, "/viajes", "/viajes/*")
                                .hasAnyRole("ADMIN_SISTEMA", "ADMIN_SUCURSAL", "CAJERO", "CHOFER")
                        .requestMatchers(HttpMethod.POST, "/viajes")
                                .hasRole("ADMIN_SUCURSAL")
                        .requestMatchers(HttpMethod.PUT, "/viajes/*")
                                .hasRole("ADMIN_SUCURSAL")
                        .requestMatchers(HttpMethod.PATCH, "/viajes/*/asignacion")
                                .hasRole("ADMIN_SUCURSAL")
                        .requestMatchers(HttpMethod.DELETE, "/viajes/*")
                                .hasRole("ADMIN_SUCURSAL")
                        .requestMatchers(HttpMethod.POST, "/viajes/*/salida", "/viajes/*/llegada")
                                .hasAnyRole("ADMIN_SUCURSAL", "CHOFER")
                        // ---------------------------------------
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
