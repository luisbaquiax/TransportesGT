package org.transportsgt.privaterentalservice.integracion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Validaciones de la solicitud en línea de punta a punta (fechas, coordenadas, sucursal y perfil).
 */
class SolicitudValidacionIT extends IntegracionBase {

    private static final OffsetDateTime SALIDA = OffsetDateTime.now().plusDays(12).withNano(0);

    private String token;

    @BeforeEach
    void perfil() {
        UUID idUsuario = UUID.randomUUID();
        jdbc.update("INSERT INTO cliente_ref (id_usuario, nit, nombre_completo, activo, version) "
                + "VALUES (?, '7777777-7', 'Validacion Prueba', true, 0) ON CONFLICT DO NOTHING", idUsuario);
        token = tokenClienteConPerfil(idUsuario, null);
    }

    private ResultActions enviar(String extra, UUID sucursal, OffsetDateTime salida) throws Exception {
        return mockMvc.perform(conToken(post("/alquileres/solicitudes"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("""
                        {"idSucursalOrigen":"%s","origen":"Quetzaltenango","destino":"Antigua",
                         "fechaHoraSalida":"%s","numeroPasajeros":20,"distanciaEstimadaKm":"180.00"%s}"""
                        .formatted(sucursal, salida, extra))));
    }

    private ResultActions enviar(String extra) throws Exception {
        return enviar(extra, XELA, SALIDA);
    }

    @Test
    void idaYVueltaSinRetornoORetornoSinIdaYVueltaSonInvalidos() throws Exception {
        enviar(",\"idaYVuelta\":true").andExpect(status().isBadRequest());
        enviar(",\"idaYVuelta\":false,\"fechaHoraRetorno\":\"%s\"".formatted(SALIDA.plusDays(1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retornoAntesDeLaSalidaYSalidaPasadaSonInvalidos() throws Exception {
        enviar(",\"idaYVuelta\":true,\"fechaHoraRetorno\":\"%s\"".formatted(SALIDA.minusHours(1)))
                .andExpect(status().isBadRequest());
        enviar("", XELA, OffsetDateTime.now().minusDays(1).withNano(0)).andExpect(status().isBadRequest());
    }

    @Test
    void idaYVueltaValidaSeRegistra() throws Exception {
        enviar(",\"idaYVuelta\":true,\"fechaHoraRetorno\":\"%s\"".formatted(SALIDA.plusDays(2)))
                .andExpect(status().isCreated());
    }

    @Test
    void coordenadasIncompletasOFueraDeRango() throws Exception {
        enviar(",\"latitudOrigen\":14.8")
                .andExpect(jsonPath("$.codigo").value("COORDENADAS_INCOMPLETAS"));
        enviar(",\"latitudOrigen\":95,\"longitudOrigen\":-91.5,\"latitudDestino\":14.6,\"longitudDestino\":-90.7")
                .andExpect(jsonPath("$.codigo").value("COORDENADAS_INVALIDAS"));
        enviar(",\"latitudOrigen\":14.8,\"longitudOrigen\":-191.5,\"latitudDestino\":14.6,\"longitudDestino\":-90.7")
                .andExpect(jsonPath("$.codigo").value("COORDENADAS_INVALIDAS"));
    }

    @Test
    void coordenadasCompletasSeRegistra() throws Exception {
        enviar(",\"latitudOrigen\":14.8347,\"longitudOrigen\":-91.5181,"
                + "\"latitudDestino\":14.5586,\"longitudDestino\":-90.7295")
                .andExpect(status().isCreated());
    }

    @Test
    void sucursalInexistenteEsInvalida() throws Exception {
        enviar("", UUID.randomUUID(), SALIDA).andExpect(status().isBadRequest());
    }

    @Test
    void clienteSinPerfilNoPuedeSolicitar() throws Exception {
        token = tokenClienteConPerfil(UUID.randomUUID(), null);
        enviar("").andExpect(jsonPath("$.codigo").value("PERFIL_INCOMPLETO"));
    }

    @Test
    void erroresDeLaSolicitud() throws Exception {
        mockMvc.perform(conToken(post("/alquileres/solicitudes"), token)
                        .contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(conToken(post("/alquileres/solicitudes"), token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(conToken(get("/alquileres/no-es-uuid"), tokenAdminSistema()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(conToken(get("/alquileres/mios").param("tamano", "500"), token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(conToken(get("/alquileres/" + UUID.randomUUID()), tokenAdminSistema()))
                .andExpect(status().isNotFound());
    }

    @Test
    void listarConFiltrosComoAdminSistemaYCajero() throws Exception {
        mockMvc.perform(conToken(get("/alquileres").param("estado", "SOLICITADO")
                        .param("desde", "2026-01-01").param("hasta", "2027-12-31").param("sinAsignar", "true"),
                        tokenAdminSistema()))
                .andExpect(status().isOk());
        mockMvc.perform(conToken(get("/alquileres"), tokenCajero()))
                .andExpect(status().isOk());
    }
}
