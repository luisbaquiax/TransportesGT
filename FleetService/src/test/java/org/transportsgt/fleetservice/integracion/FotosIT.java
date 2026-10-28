package org.transportsgt.fleetservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.transportsgt.eventos.payload.identidad.Rol;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fotos privadas de buses/choferes: subida a almacenamiento local (directorio de prueba), lectura con token por
 * GET /buses/{id}/foto y /choferes/{id}/foto con autorización por sucursal, reemplazo y validación de formato.
 * La ruta anterior GET /fotos/** ya no es pública (queda para administradores mientras se retira).
 */
class FotosIT extends IntegracionBase {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10,
            0x4A, 0x46, 0x49, 0x46, 0, 1, 1, 0, 0, 1, 0, 1, 0, 0, (byte) 0xFF, (byte) 0xD9};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0, 0, 0, 0x0D, 0x49, 0x48, 0x44, 0x52, 0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0,
            (byte) 0x90, 0x77, 0x53, (byte) 0xDE, 0, 0, 0, 0, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE,
            0x42, 0x60, (byte) 0x82};

    private String subirFotoBus(String id, MockMultipartFile archivo) throws Exception {
        return leer(mockMvc.perform(conToken(multipart("/buses/" + id + "/foto").file(archivo), tokenAdminSucursal()))
                .andExpect(status().isOk())
                .andReturn()).get("fotoUrl").asString();
    }

    @Test
    void subirFotoDeBusYLeerlaConToken() throws Exception {
        String id = crearBus(unico("PFOT"));
        String fotoUrl = subirFotoBus(id, new MockMultipartFile("archivo", "bus-frontal.jpg", "image/jpeg", JPEG));

        // La respuesta no expone la referencia interna ni la URL del almacenamiento
        assertThat(fotoUrl).startsWith("/buses/" + id + "/foto?v=");
        mockMvc.perform(conToken(get("/buses/" + id + "/foto"), tokenAdminSucursal()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_JPEG))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("private")))
                .andExpect(content().bytes(JPEG));
        // ADMIN_SISTEMA consulta todas las sucursales
        mockMvc.perform(conToken(get("/buses/" + id + "/foto"), tokenAdminSistema()))
                .andExpect(status().isOk());
    }

    @Test
    void fotoDeBusEsPrivada() throws Exception {
        String id = crearBus(unico("PPRI"));
        subirFotoBus(id, new MockMultipartFile("archivo", "privada.jpg", "image/jpeg", JPEG));

        mockMvc.perform(get("/buses/" + id + "/foto"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(conToken(get("/buses/" + id + "/foto"), token(Rol.CLIENTE, null)))
                .andExpect(status().isForbidden());
        // Administrador de otra sucursal
        mockMvc.perform(conToken(get("/buses/" + id + "/foto"), token(Rol.ADMIN_SUCURSAL, CAPITAL)))
                .andExpect(status().isForbidden());
        // La ruta anterior ya no es pública
        String nombre = nombreFoto("SELECT foto_url FROM bus WHERE id = ?", id);
        mockMvc.perform(get("/fotos/buses/" + nombre))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(conToken(get("/fotos/buses/" + nombre), token(Rol.CLIENTE, null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void busSinFotoDevuelve404() throws Exception {
        String id = crearBus(unico("PSIN"));
        mockMvc.perform(conToken(get("/buses/" + id), tokenAdminSucursal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotoUrl").isEmpty());
        mockMvc.perform(conToken(get("/buses/" + id + "/foto"), tokenAdminSucursal()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("FOTO_NO_ENCONTRADA"));
    }

    @Test
    void reemplazarConOtraExtensionEliminaLaAnterior() throws Exception {
        String id = crearBus(unico("PREP"));
        String urlJpg = subirFotoBus(id, new MockMultipartFile("archivo", "cambio.jpg", "image/jpeg", JPEG));
        String nombreJpg = nombreFoto("SELECT foto_url FROM bus WHERE id = ?", id);

        String urlPng = subirFotoBus(id, new MockMultipartFile("archivo", "cambio.png", "image/png", PNG));
        String nombrePng = nombreFoto("SELECT foto_url FROM bus WHERE id = ?", id);

        assertThat(nombrePng).endsWith(".png").isNotEqualTo(nombreJpg);
        assertThat(urlPng).isNotEqualTo(urlJpg);
        mockMvc.perform(conToken(get("/buses/" + id + "/foto"), tokenAdminSucursal()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(PNG));
        // La anterior ya no existe (se borró del almacenamiento)
        mockMvc.perform(conToken(get("/fotos/buses/" + nombreJpg), tokenAdminSucursal()))
                .andExpect(status().isNotFound());
    }

    @Test
    void reemplazarConLaMismaExtensionCambiaLaFotoUrl() throws Exception {
        // Misma clave en el almacenamiento: la ?v= debe cambiar para que el navegador no use su caché
        String id = crearBus(unico("PMIS"));
        String primera = subirFotoBus(id, new MockMultipartFile("archivo", "a.jpg", "image/jpeg", JPEG));
        String segunda = subirFotoBus(id, new MockMultipartFile("archivo", "b.jpg", "image/jpeg", JPEG));
        assertThat(segunda).isNotEqualTo(primera);
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
    void fotoDeChoferPrivada() throws Exception {
        String id = crearChofer(unico("LICF"));
        MockMultipartFile archivo =
                new MockMultipartFile("archivo", "chofer.jpg", "image/jpeg", JPEG);
        mockMvc.perform(conToken(multipart("/choferes/" + id + "/foto").file(archivo), tokenAdminSucursal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotoUrl").value(org.hamcrest.Matchers.startsWith("/choferes/" + id + "/foto?v=")));
        mockMvc.perform(conToken(get("/choferes/" + id + "/foto"), tokenAdminSucursal()))
                .andExpect(status().isOk())
                .andExpect(content().bytes(JPEG));
        mockMvc.perform(get("/choferes/" + id + "/foto"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(conToken(get("/choferes/" + id + "/foto"), token(Rol.ADMIN_SUCURSAL, CAPITAL)))
                .andExpect(status().isForbidden());
    }

    @Test
    void busInexistenteDevuelve404() throws Exception {
        MockMultipartFile archivo =
                new MockMultipartFile("archivo", "x.jpg", "image/jpeg", JPEG);
        mockMvc.perform(conToken(multipart("/buses/" + UUID.randomUUID() + "/foto").file(archivo),
                        tokenAdminSucursal()))
                .andExpect(status().isNotFound());
        mockMvc.perform(conToken(get("/buses/" + UUID.randomUUID() + "/foto"), tokenAdminSucursal()))
                .andExpect(status().isNotFound());
    }

    private String nombreFoto(String consulta, String id) {
        String url = jdbc.queryForObject(consulta, String.class, UUID.fromString(id));
        return url.substring(url.lastIndexOf('/') + 1);
    }
}
