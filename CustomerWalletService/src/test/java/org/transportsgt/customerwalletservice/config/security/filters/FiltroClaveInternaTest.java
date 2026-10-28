package org.transportsgt.customerwalletservice.config.security.filters;

import org.transportsgt.customerwalletservice.config.security.handlers.EscritorRespuestaError;

import org.transportsgt.customerwalletservice.exception.FabricaRespuestaError;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FiltroClaveInternaTest {

    private static final String CLAVE = "secreto-compartido";

    private FiltroClaveInterna filtro;
    private MockHttpServletResponse respuesta;
    private MockFilterChain cadena;

    @BeforeEach
    void setUp() {
        filtro = new FiltroClaveInterna(CLAVE, new EscritorRespuestaError(JsonMapper.builder().build(), new FabricaRespuestaError()));
        respuesta = new MockHttpServletResponse();
        cadena = new MockFilterChain();
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private static MockHttpServletRequest peticion(String metodo, String uri, String clave) {
        MockHttpServletRequest request = new MockHttpServletRequest(metodo, "/v1/customers" + uri);
        request.setContextPath("/v1/customers");
        if (clave != null) {
            request.addHeader(FiltroClaveInterna.CABECERA, clave);
        }
        return request;
    }

    @Nested
    class Constructor {

        @Test
        void constructor_cuandoLaClaveEstaVacia_fallaAlArrancar() {
            EscritorRespuestaError escritor = new EscritorRespuestaError(JsonMapper.builder().build(), new FabricaRespuestaError());
            assertAll(
                    () -> assertThrows(IllegalStateException.class, () -> new FiltroClaveInterna(" ", escritor)),
                    () -> assertThrows(IllegalStateException.class, () -> new FiltroClaveInterna(null, escritor))
            );
        }
    }

    @Nested
    class DoFilter {

        @Test
        void doFilter_cuandoLaClaveCoincide_autenticaComoInterno() throws Exception {
            // Act
            filtro.doFilter(peticion("GET", "/cartera/pagos", CLAVE), respuesta, cadena);

            // Assert
            Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
            assertAll(
                    () -> assertNotNull(cadena.getRequest()),
                    () -> assertNotNull(autenticacion),
                    () -> assertTrue(autenticacion.isAuthenticated()),
                    () -> assertEquals(FiltroClaveInterna.ROL_INTERNO,
                            autenticacion.getAuthorities().iterator().next().getAuthority())
            );
        }

        @Test
        void doFilter_cuandoLaRutaTraeBarraFinal_tambienActua() throws Exception {
            // Act
            filtro.doFilter(peticion("GET", "/cartera/pagos/", CLAVE), respuesta, cadena);

            // Assert
            assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        }

        @Test
        void doFilter_cuandoLaClaveNoCoincide_responde401SinContinuar() throws Exception {
            // Act
            filtro.doFilter(peticion("GET", "/cartera/pagos", "otra-clave"), respuesta, cadena);

            // Assert
            assertAll(
                    () -> assertEquals(401, respuesta.getStatus()),
                    () -> assertTrue(respuesta.getContentAsString().contains("NO_AUTENTICADO")),
                    () -> assertNull(cadena.getRequest()),
                    () -> assertNull(SecurityContextHolder.getContext().getAuthentication())
            );
        }

        @Test
        void doFilter_cuandoTraeAuthorization_ignoraLaClave() throws Exception {
            // Arrange
            MockHttpServletRequest request = peticion("GET", "/cartera/pagos", "otra-clave");
            request.addHeader("Authorization", "Bearer abc");

            // Act
            filtro.doFilter(request, respuesta, cadena);

            // Assert
            assertAll(
                    () -> assertNotNull(cadena.getRequest()),
                    () -> assertNull(SecurityContextHolder.getContext().getAuthentication())
            );
        }

        @Test
        void doFilter_enOtraRutaOMetodo_ignoraLaClave() throws Exception {
            // Arrange
            MockFilterChain otraCadena = new MockFilterChain();
            MockFilterChain sinClave = new MockFilterChain();

            // Act
            filtro.doFilter(peticion("POST", "/cartera/pagos", "otra-clave"), respuesta, cadena);
            filtro.doFilter(peticion("GET", "/cartera/yo", "otra-clave"), new MockHttpServletResponse(), otraCadena);
            filtro.doFilter(peticion("GET", "/cartera/pagos", null), new MockHttpServletResponse(), sinClave);

            // Assert
            assertAll(
                    () -> assertNotNull(cadena.getRequest()),
                    () -> assertNotNull(otraCadena.getRequest()),
                    () -> assertNotNull(sinClave.getRequest()),
                    () -> assertNull(SecurityContextHolder.getContext().getAuthentication())
            );
        }
    }
}
