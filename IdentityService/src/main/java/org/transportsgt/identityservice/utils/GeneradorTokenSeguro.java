package org.transportsgt.identityservice.utils;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Tokens de un solo uso para los enlaces de contraseña.
 * El token en claro NUNCA se guarda ni se escribe en el log: en la base solo va su SHA-256.
 */
@Component
public class GeneradorTokenSeguro {

    private static final int BYTES_TOKEN = 32;

    private final SecureRandom random = new SecureRandom();

    /**
     * 32 bytes aleatorios en Base64 URL-safe sin relleno (43 caracteres).
     */
    public String generar() {
        byte[] bytes = new byte[BYTES_TOKEN];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 en hexadecimal (64 caracteres), el valor de {@code token_contrasena.hash_token}.
     */
    public String sha256Hex(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en la JVM", e);
        }
    }
}
