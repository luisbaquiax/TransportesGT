package org.transportsgt.operationalcostsservice.config.security;

import org.transportsgt.operationalcostsservice.config.security.handlers.JsonAccessDeniedHandler;
import org.transportsgt.operationalcostsservice.config.security.handlers.JsonAuthenticationEntryPoint;

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
 * <p>Rutas relativas al context-path {@code /v1/costs}.
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
                        // Depreciación
                        .requestMatchers(HttpMethod.GET, "/costos/depreciacion/tarifa-vigente")
                                .hasAnyRole("ADMIN_SISTEMA", "ADMIN_SUCURSAL")
                        .requestMatchers(HttpMethod.GET, "/costos/depreciacion/tarifas")
                                .hasRole("ADMIN_SISTEMA")
                        .requestMatchers(HttpMethod.POST, "/costos/depreciacion/tarifas")
                                .hasRole("ADMIN_SISTEMA")
                        // Categorías (catálogo administrable)
                        .requestMatchers("/costos/categorias/**").hasAnyRole("ADMIN_SISTEMA", "ADMIN_SUCURSAL")
                        // Taller: registrar es de sucursal; lectura SUC+SIS
                        .requestMatchers(HttpMethod.POST, "/costos/taller")
                                .hasRole("ADMIN_SUCURSAL")
                        .requestMatchers("/costos/taller/**", "/costos/viajes/**")
                                .hasAnyRole("ADMIN_SISTEMA", "ADMIN_SUCURSAL")
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
