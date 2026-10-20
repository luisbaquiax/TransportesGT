package org.transportsgt.ticketservice.config.security;

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
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Sin CORS (lo resuelve el gateway) y sin login (lo hace Identidad).
 *
 * <p>Rutas relativas al context-path {@code /v1/tickets}.
 * Lo único que cambia entre servicios es el bloque de reglas (y aquí, el filtro de la clave interna).
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
                                                   JsonAccessDeniedHandler accessDeniedHandler,
                                                   FiltroClaveInterna filtroClaveInterna) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(RUTAS_PUBLICAS).permitAll()
                        // Reglas propias de Boletos
                        .requestMatchers("/boletos/ventas-mostrador").hasAnyRole("CAJERO", "ADMIN_SUCURSAL")
                        .requestMatchers(HttpMethod.GET, "/boletos/compras").hasAnyRole("CAJERO", "ADMIN_SUCURSAL")
                        // JWT reenviado por Viajes o X-Clave-Interna
                        .requestMatchers(HttpMethod.GET, "/boletos/viajes/*/asiento-maximo").authenticated()
                        .requestMatchers("/boletos/**").authenticated()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(oauth -> oauth
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                // X-Clave-Interna (solo GET /boletos/viajes/{idViaje}/asiento-maximo sin Authorization)
                .addFilterBefore(filtroClaveInterna, BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    /**
     * El filtro solo debe correr dentro de la cadena de seguridad, no también como filtro de servlet.
     */
    @Bean
    public FilterRegistrationBean<FiltroClaveInterna> registroFiltroClaveInterna(FiltroClaveInterna filtro) {
        FilterRegistrationBean<FiltroClaveInterna> registro = new FilterRegistrationBean<>(filtro);
        registro.setEnabled(false);
        return registro;
    }
}
