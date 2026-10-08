package org.transportsgt.identityservice.utils;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UtilsTest {

    private final GeneradorTokenSeguro generador = new GeneradorTokenSeguro();

    @Test
    void generar_produce43CaracteresUrlSafeSinRelleno() {
        // Act
        String token = generador.generar();

        // Assert
        assertAll(
                () -> assertEquals(43, token.length()),   // 32 bytes en Base64 sin '='
                () -> assertTrue(token.matches("^[A-Za-z0-9_-]+$"))
        );
    }

    @Test
    void generar_noRepiteValores() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            tokens.add(generador.generar());
        }
        assertEquals(100, tokens.size());
    }

    @Test
    void sha256Hex_esDeterministaYDe64Caracteres() {
        // Act
        String hash = generador.sha256Hex("abc");

        // Assert
        assertAll(
                () -> assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash),
                () -> assertEquals(64, hash.length()),
                () -> assertNotEquals(hash, generador.sha256Hex("abd"))
        );
    }

    @Test
    void normalizar_quitaEspaciosYPasaAMinusculas() {
        assertAll(
                () -> assertEquals("ana@transportes.gt", Correos.normalizar("  Ana@Transportes.GT ")),
                () -> assertNull(Correos.normalizar(null))
        );
    }

    @Test
    void enmascarar_ocultaLaParteLocal() {
        assertAll(
                () -> assertEquals("a***z@transportes.gt", Correos.enmascarar("ana.lopez@transportes.gt")),
                () -> assertEquals("a***@x.gt", Correos.enmascarar("ab@x.gt")),
                () -> assertEquals("***", Correos.enmascarar("sinarroba")),
                () -> assertNull(Correos.enmascarar(null))
        );
    }

    @Test
    void ipCliente_usaLaPrimeraDeXForwardedFor() {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "200.1.2.3, 10.0.0.1");
        request.setRemoteAddr("10.0.0.1");

        // Assert
        assertEquals("200.1.2.3", IpCliente.de(request));
    }

    @Test
    void ipCliente_sinCabeceraUsaRemoteAddr() {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.10");

        // Assert
        assertEquals("192.168.1.10", IpCliente.de(request));
    }

    @Test
    void ipCliente_recortaA45Caracteres() {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "x".repeat(60));

        // Assert
        assertEquals(45, IpCliente.de(request).length());
    }

    @Test
    void ipCliente_sinDatosDevuelveNull() {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(null);

        // Assert
        assertNull(IpCliente.de(request));
    }
}
