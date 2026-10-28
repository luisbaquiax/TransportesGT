package org.transportsgt.fleetservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.transportsgt.eventos.payload.identidad.Rol;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Casos alternos de buses, choferes y fotos: sin cambios, duplicados, versiones, sucursales y archivos inválidos.
 */
class CasosFlotaIT extends IntegracionBase {

    private ResultActions enviar(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion,
                                 String cuerpo) throws Exception {
        return mockMvc.perform(conToken(peticion, tokenAdminSucursal())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions crearBusCon(String token, String placa, String kilometraje) throws Exception {
        return mockMvc.perform(conToken(post("/buses"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("""
                        {"placa":"%s","marca":"Volvo","modelo":"B7R","anioFabricacion":2019,
                         "capacidadPasajeros":45%s}""".formatted(placa, kilometraje))));
    }

    private static String placa() {
        return "C" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    @Test
    void busSinKilometrajeYCambiosSinEfecto() throws Exception {
        String id = leer(crearBusCon(tokenAdminSucursal(), placa(), "").andExpect(status().isCreated())
                .andReturn()).get("id").asString();

        enviar(patch("/buses/" + id + "/activo"), "{\"activo\":true}").andExpect(status().isOk());
        enviar(patch("/buses/" + id + "/estado-operativo"), "{\"estadoOperativo\":\"DISPONIBLE\"}")
                .andExpect(status().isOk());
        enviar(patch("/buses/" + id + "/estado-operativo"), "{\"estadoOperativo\":\"EN_TRANSITO\"}")
                .andExpect(status().is4xxClientError());
        enviar(patch("/buses/" + id + "/activo"), "{\"activo\":false,\"version\":999}")
                .andExpect(status().isConflict());
        enviar(put("/buses/" + id), """
                {"placa":"%s","marca":"Volvo","modelo":"B7R","anioFabricacion":2019,"capacidadPasajeros":45}"""
                .formatted(PLACA_SEMILLA)).andExpect(status().isConflict());
        mockMvc.perform(conToken(get("/buses/" + id + "/foto"), tokenAdminSucursal()))
                .andExpect(status().isNotFound());
    }

    @Test
    void desactivarBusConsultaViajes() throws Exception {
        String id = crearBus(placa());
        // Viajes no corre en las pruebas: falla cerrado
        enviar(patch("/buses/" + id + "/activo"), "{\"activo\":false}").andExpect(status().is5xxServerError());
    }

    @Test
    void sucursalInexistenteInactivaUOtra() throws Exception {
        UUID inactiva = UUID.randomUUID();
        jdbc.update("INSERT INTO sucursal_ref (id, nombre, activa, version) VALUES (?, 'Cerrada', FALSE, 0)", inactiva);
        crearBusCon(token(Rol.ADMIN_SUCURSAL, UUID.randomUUID()), placa(), "").andExpect(status().is4xxClientError());
        crearBusCon(token(Rol.ADMIN_SUCURSAL, inactiva), placa(), "").andExpect(status().is4xxClientError());

        String id = crearBus(placa());
        mockMvc.perform(conToken(put("/buses/" + id), token(Rol.ADMIN_SUCURSAL, CAPITAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"placa":"X1","marca":"V","modelo":"M","anioFabricacion":2019,"capacidadPasajeros":4}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void choferConLicenciaOUsuarioRepetidos() throws Exception {
        String licencia = unico("LIC");
        String id = crearChofer(licencia);
        String otro = crearChofer(unico("LIC"));
        UUID idUsuario = UUID.randomUUID();
        String cuerpo = """
                {"nombreCompleto":"Chofer Editado","correo":"editado@transportes.gt","telefono":"5555-1111",
                 "numeroLicencia":"%s","tipoLicencia":"B","fechaVencimientoLicencia":"2029-01-01",
                 "salarioBaseViaje":"160.00"%s}""";

        enviar(put("/choferes/" + id), cuerpo.formatted(licencia, ",\"idUsuario\":\"" + idUsuario + "\""))
                .andExpect(status().isOk());
        enviar(put("/choferes/" + otro), cuerpo.formatted(licencia, "")).andExpect(status().isConflict());
        enviar(put("/choferes/" + otro), cuerpo.formatted(unico("LIC"), ",\"idUsuario\":\"" + idUsuario + "\""))
                .andExpect(status().isConflict());
        enviar(patch("/choferes/" + otro + "/activo"), "{\"activo\":false}").andExpect(status().isOk());
    }

    @Test
    void listarChoferesConFiltros() throws Exception {
        mockMvc.perform(conToken(get("/choferes").param("activo", "true").param("texto", "chofer"),
                        tokenAdminSucursal()))
                .andExpect(status().isOk());
        mockMvc.perform(conToken(get("/choferes").param("texto", " "), tokenAdminSistema()))
                .andExpect(status().isOk());
        mockMvc.perform(conToken(get("/buses").param("estadoOperativo", "DISPONIBLE").param("activo", "true")
                        .param("texto", "p9"), tokenAdminSucursal()))
                .andExpect(status().isOk());
        mockMvc.perform(conToken(get("/buses").param("texto", " "), tokenAdminSucursal()))
                .andExpect(status().isOk());
        mockMvc.perform(conToken(get("/buses").param("idSucursal", CAPITAL.toString()), tokenAdminSucursal()))
                .andExpect(status().isForbidden());
        mockMvc.perform(conToken(get("/fotos/desconocido/x.jpg"), tokenAdminSistema()))
                .andExpect(status().isNotFound());
        String chofer = crearChofer(unico("LICA"));
        enviar(patch("/choferes/" + chofer + "/activo"), "{\"activo\":true}").andExpect(status().isOk());
    }

    @Test
    void fotosInvalidas() throws Exception {
        String id = crearBus(placa());
        mockMvc.perform(conToken(multipart("/buses/" + id + "/foto")
                        .file(new MockMultipartFile("archivo", "vacia.jpg", "image/jpeg", new byte[0])),
                        tokenAdminSucursal()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(conToken(multipart("/buses/" + id + "/foto")
                        .file(new MockMultipartFile("archivo", "grande.jpg", "image/jpeg", new byte[6 * 1024 * 1024])),
                        tokenAdminSucursal()))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(conToken(multipart("/buses/" + id + "/foto")
                        .file(new MockMultipartFile("archivo", "texto.gif", "image/gif", "no es imagen".getBytes())),
                        tokenAdminSucursal()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(conToken(get("/fotos/buses/..%2Fsecreto.jpg"), tokenAdminSistema()))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(conToken(get("/fotos/buses/no-existe.jpg"), tokenAdminSistema()))
                .andExpect(status().isNotFound());
    }

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0, 0, 0, 0x0D, 0x49, 0x48, 0x44, 0x52, 0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0,
            (byte) 0x90, 0x77, 0x53, (byte) 0xDE, 0, 0, 0, 0, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE,
            0x42, 0x60, (byte) 0x82};

    @Test
    void fotosPngDeBusYChoferSeLeenPorRutaYPorNombre() throws Exception {
        String idBus = crearBus(placa());
        String idChofer = crearChofer(unico("LICF"));
        mockMvc.perform(conToken(multipart("/buses/" + idBus + "/foto")
                        .file(new MockMultipartFile("archivo", "bus.png", "image/png", PNG)), tokenAdminSucursal()))
                .andExpect(status().isOk());
        mockMvc.perform(conToken(multipart("/choferes/" + idChofer + "/foto")
                        .file(new MockMultipartFile("archivo", "chofer.png", "image/png", PNG)), tokenAdminSucursal()))
                .andExpect(status().isOk());

        mockMvc.perform(conToken(get("/buses/" + idBus + "/foto"), tokenAdminSucursal())).andExpect(status().isOk());
        mockMvc.perform(conToken(get("/choferes/" + idChofer + "/foto"), tokenAdminSucursal()))
                .andExpect(status().isOk());
        // foto_url guarda la URL pública (.../fotos/buses/<nombre>)
        String url = jdbc.queryForObject("SELECT foto_url FROM bus WHERE id = ?::uuid", String.class, idBus);
        mockMvc.perform(conToken(get(url.substring(url.indexOf("/fotos/"))), tokenAdminSistema()))
                .andExpect(status().isOk());
        mockMvc.perform(conToken(get("/fotos/buses/con espacio.jpg"), tokenAdminSistema()))
                .andExpect(status().isNotFound());
        mockMvc.perform(conToken(multipart("/buses/" + idBus + "/foto")
                        .file(new MockMultipartFile("archivo", "sin-tipo", null, PNG)), tokenAdminSucursal()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sucursalSinVersion() {
        UUID idEvento = UUID.randomUUID();
        UUID idSucursal = UUID.randomUUID();
        rabbitTemplate.convertAndSend("transporte.eventos", rutaDe("sucursal"), """
                {"idEvento":"%s","tipoEvento":"SucursalCreada","versionEvento":1,
                 "ocurridoEn":"2026-10-17T12:00:00Z","servicioProductor":"servicio-identidad",
                 "idAgregado":"%s","versionAgregado":null,"idCorrelacion":"%s",
                 "datos":{"idSucursal":"%s","nombre":"Nueva","departamento":"Petén","direccion":"Centro",
                          "latitud":null,"longitud":null,"activa":true}}
                """.formatted(idEvento, idSucursal, UUID.randomUUID(), idSucursal));
        esperar(() -> jdbc.queryForObject("SELECT count(*) FROM evento_procesado WHERE id_evento = ?",
                Integer.class, idEvento) == 1, 15);
    }

    @Test
    void erroresDeLaSolicitud() throws Exception {
        String sistema = tokenAdminSistema();
        mockMvc.perform(conToken(get("/buses/no-es-uuid"), sistema)).andExpect(status().isBadRequest());
        mockMvc.perform(conToken(get("/buses/" + UUID.randomUUID()), sistema)).andExpect(status().isNotFound());
        mockMvc.perform(conToken(get("/choferes/" + UUID.randomUUID()), sistema)).andExpect(status().isNotFound());
        enviar(post("/buses"), "{no es json").andExpect(status().isBadRequest());
        enviar(post("/buses"), "{}").andExpect(status().isBadRequest());
        mockMvc.perform(conToken(put("/buses"), tokenAdminSucursal())).andExpect(status().is4xxClientError());
        mockMvc.perform(conToken(get("/no-existe"), sistema)).andExpect(status().isNotFound());
    }
}
