package org.transportsgt.fleetservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fotos de buses/choferes: subida a almacenamiento local (directorio de prueba),
 * servida sin token (ruta pública), reemplazo de extensión y validación de formato.
 */
class FotosIntegrationTest extends IntegracionBase {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10,
            0x4A, 0x46, 0x49, 0x46, 0, 1, 1, 0, 0, 1, 0, 1, 0, 0, (byte) 0xFF, (byte) 0xD9};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0, 0, 0, 0x0D, 0x49, 0x48, 0x44, 0x52, 0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0,
            (byte) 0x90, 0x77, 0x53, (byte) 0xDE, 0, 0, 0, 0, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE,
            0x42, 0x60, (byte) 0x82};

    @Test
    void subirFotoDeBusYServirlaSinToken() throws Exception {
        String id = crearBus(unico("PFOT"));
        MockMultipartFile archivo =
                new MockMultipartFile("archivo", "bus-frontal.jpg", "image/jpeg", JPEG);

        mockMvc.perform(conToken(multipart("/buses/" + id + "/foto").file(archivo), tokenAdminSucursal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.fotoUrl").isNotEmpty());

        String nombre = nombreFotoDeBus(id);
        // Pública: el frontend la pide en <img src> sin Authorization
        mockMvc.perform(get("/fotos/buses/" + nombre))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(JPEG));
    }

    @Test
    void reemplazarConOtraExtensionEliminaLaAnterior() throws Exception {
        String id = crearBus(unico("PREP"));
        MockMultipartFile jpg =
                new MockMultipartFile("archivo", "cambio.jpg", "image/jpeg", JPEG);
        MockMultipartFile png =
                new MockMultipartFile("archivo", "cambio.png", "image/png", PNG);

        mockMvc.perform(conToken(multipart("/buses/" + id + "/foto").file(jpg), tokenAdminSucursal()))
                .andExpect(status().isOk());
        String nombreJpg = nombreFotoDeBus(id);

        mockMvc.perform(conToken(multipart("/buses/" + id + "/foto").file(png), tokenAdminSucursal()))
                .andExpect(status().isOk());
        String nombrePng = nombreFotoDeBus(id);

        assertThat(nombrePng).endsWith(".png").isNotEqualTo(nombreJpg);
        mockMvc.perform(get("/fotos/buses/" + nombrePng)).andExpect(status().isOk());
        // La anterior ya no existe (en disco se borró y el registro se actualizó)
        mockMvc.perform(get("/fotos/buses/" + nombreJpg)).andExpect(status().isNotFound());
    }

    @Test
    void formatoNoPermitidoDevuelve400() throws Exception {
        String id = crearBus(unico("PBAD"));
        MockMultipartFile gif =
                new MockMultipartFile("archivo", "anim.gif", "image/gif", new byte[]{'G', 'I', 'F'});
        mockMvc.perform(conToken(multipart("/buses/" + id + "/foto").file(gif), tokenAdminSucursal()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("FOTO_INVALIDA"));
    }

    @Test
    void fotoDeChoferYRutaPublica() throws Exception {
        String id = crearChofer(unico("LICF"));
        MockMultipartFile archivo =
                new MockMultipartFile("archivo", "chofer.jpg", "image/jpeg", JPEG);
        mockMvc.perform(conToken(multipart("/choferes/" + id + "/foto").file(archivo), tokenAdminSucursal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotoUrl").isNotEmpty());
        String nombre = nombreFoto("SELECT foto_url FROM chofer WHERE id = ?", id);
        mockMvc.perform(get("/fotos/choferes/" + nombre)).andExpect(status().isOk());
    }

    private String nombreFotoDeBus(String idBus) {
        return nombreFoto("SELECT foto_url FROM bus WHERE id = ?", idBus);
    }

    private String nombreFoto(String consulta, String id) {
        String url = jdbc.queryForObject(consulta, String.class, java.util.UUID.fromString(id));
        return url.substring(url.lastIndexOf('/') + 1);
    }

    @Test
    void busInexistenteDevuelve404() throws Exception {
        MockMultipartFile archivo =
                new MockMultipartFile("archivo", "x.jpg", "image/jpeg", JPEG);
        mockMvc.perform(conToken(multipart("/buses/" + UUID.randomUUID() + "/foto").file(archivo),
                        tokenAdminSucursal()))
                .andExpect(status().isNotFound());
    }
}
