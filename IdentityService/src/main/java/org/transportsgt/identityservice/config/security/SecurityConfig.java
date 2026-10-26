package org.transportsgt.identityservice.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Sin CORS: lo resuelve el gateway. Si se agrega aquí, el navegador recibe
 * Access-Control-Allow-Origin duplicado y rechaza la respuesta.
 *
 * <p>Las rutas son relativas al context-path {@code /v1/identity}
 * ({@code /auth/login} responde en {@code /v1/identity/auth/login}).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // habilita @PreAuthorize en controladores y servicios
public class SecurityConfig {

    private static final String[] RUTAS_PUBLICAS = {
            "/auth/login",
            "/auth/registro",
            "/auth/olvide-contrasena",
            "/auth/enlace-contrasena/validar",
            "/auth/restablecer-contrasena",
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
                        // El orden importa: la regla más específica va primero.
                        // Además de @PreAuthorize: así el 403 sale antes de validar el cuerpo (que daría 400)
                        .requestMatchers(RUTAS_PUBLICAS).permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN_SISTEMA")
                        .requestMatchers(HttpMethod.GET, "/sucursales/*/administradores").hasRole("ADMIN_SISTEMA")
                        // Listado de sucursales: lo necesitan formularios de todos los roles
                        .requestMatchers(HttpMethod.GET, "/sucursales/**").authenticated()
                        .requestMatchers("/sucursales/**").hasRole("ADMIN_SISTEMA")
                        // Cualquier usuario consulta su propio registro y edita su nombre
                        .requestMatchers(HttpMethod.PUT, "/usuarios/yo").authenticated()
                        .requestMatchers(HttpMethod.GET, "/usuarios/*").authenticated()
                        .requestMatchers("/usuarios", "/usuarios/**").hasAnyRole("ADMIN_SISTEMA", "ADMIN_SUCURSAL")
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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Se usa en el login para validar correo + contraseña contra la BD.
     *
     * <p>Primero se compara la contraseña y solo después se revisa si el usuario está activo:
     * así un usuario inactivo con contraseña incorrecta recibe 401 (como cualquiera) y no 403,
     * y no se revela que la cuenta existe y está desactivada.
     */
    @Bean
    public AuthenticationManager authenticationManager(CustomUserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setPreAuthenticationChecks(usuario -> {
        });
        provider.setPostAuthenticationChecks(usuario -> {
            if (!usuario.isEnabled()) {
                throw new DisabledException("Usuario inactivo");
            }
        });
        return new ProviderManager(provider);
    }
}
