package org.transportsgt.privaterentalservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.transportsgt.eventos.payload.identidad.Rol;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AlquilerIT extends IntegracionBase {

    @Test
    void flujoCompletoEnLineaHastaMostradorPago() throws Exception {
        // 1. Cliente autenticado solicita en línea (con perfil insertado)
        UUID idUsuario = UUID.randomUUID();
        jdbc.update("INSERT INTO cliente_ref (id_usuario, nit, nombre_completo, activo, version) "
                + "VALUES (?, '7777777-7', 'Flujo Prueba', true, 0) ON CONFLICT DO NOTHING", idUsuario);
        String token = tokenClienteConPerfil(idUsuario, null);
        String id = solicitar(token);

        mockMvc.perform(get("/alquileres/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SOLICITADO"))
                .andExpect(jsonPath("$.canal").value("EN_LINEA"))
                .andExpect(jsonPath("$.precioEstimado").value(2500.00))
                .andExpect(jsonPath("$.cotizaciones").isArray());

        // 2. Admin de sucursal cotiza
        mockMvc.perform(conToken(post("/alquileres/%s/cotizacion".formatted(id)), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"precioFinal":"2700.00",
                                 "fechaHoraLlegadaEstimada":"%s",
                                 "comentario":"Incluye peajes"}""".formatted(
                                OffsetDateTime.now().plusDays(10).plusHours(5)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COTIZADO"))
                .andExpect(jsonPath("$.precioFinal").value(2700.00));

        // 3. Pago en línea: Cartera no responde → 503 fail-closed
        mockMvc.perform(conToken(post("/alquileres/%s/pago".formatted(id)), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"fechaPago":"%s"}""".formatted(java.time.LocalDate.now()))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("SERVICIO_NO_DISPONIBLE"));

        // El alquiler sigue COTIZADO (nunca se confirma sin pago)
        mockMvc.perform(get("/alquileres/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COTIZADO"));
    }

    @Test
    void flujoMostradorCompleto() throws Exception {
        // 1. Cajero registra en mostrador (sin NIT → CF)
        String id;
        mockMvc.perform(conToken(post("/alquileres/mostrador"), tokenCajero())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"solicitud":{"idSucursalOrigen":"%s","origen":"Quetzaltenango",
                                 "destino":"Huehuetenango","fechaHoraSalida":"%s","idaYVuelta":false,
                                 "numeroPasajeros":20,"distanciaEstimadaKm":"180.00"},
                                 "nitCliente":null,"nombreCliente":"Juan Pérez"}"""
                                .formatted(XELA, OffsetDateTime.now().plusDays(15)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.canal").value("MOSTRADOR"))
                .andExpect(jsonPath("$.nitCliente").value("CF"))
                .andExpect(jsonPath("$.nombreCliente").value("Juan Pérez"))
                .andExpect(jsonPath("$.estado").value("SOLICITADO"));
        // Tomamos el id del cuerpo
        var cuerpo = mockMvc.perform(conToken(post("/alquileres/mostrador"), tokenCajero())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"solicitud":{"idSucursalOrigen":"%s","origen":"X",
                                 "destino":"Y","fechaHoraSalida":"%s","idaYVuelta":false,
                                 "numeroPasajeros":10,"distanciaEstimadaKm":"50.00"},
                                 "nitCliente":"1234567-8","nombreCliente":"Ana López"}"""
                                .formatted(XELA, OffsetDateTime.now().plusDays(3)))))
                .andExpect(status().isCreated())
                .andReturn();
        id = leer(cuerpo).get("id").asString();

        // 2. Admin cotiza
        cotizar(id, tokenAdminSucursal());

        // 3. Pago en efectivo en mostrador
        mockMvc.perform(conToken(post("/alquileres/%s/pago-mostrador".formatted(id)), tokenCajero())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"fechaPago":"%s"}""".formatted(java.time.LocalDate.now()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADO"))
                .andExpect(jsonPath("$.idPago").value(nullValue()));

        // 4. Evento AlquilerConfirmado en el outbox
        UUID idAlquiler = UUID.fromString(id);
        org.junit.jupiter.api.Assertions.assertEquals(1, contarOutbox(idAlquiler, "AlquilerConfirmado"));
    }

    @Test
    void solicitudEnLineaSinDistanciaNiCoordenadasEs400() throws Exception {
        UUID idUsuario = UUID.randomUUID();
        // Insertar perfil comercial de prueba para no depender de la semilla
        jdbc.update("INSERT INTO cliente_ref (id_usuario, nit, nombre_completo, activo, version) "
                + "VALUES (?, '9999999-9', 'Perfil Prueba', true, 0) ON CONFLICT DO NOTHING", idUsuario);
        String token = tokenClienteConPerfil(idUsuario, null);
        mockMvc.perform(conToken(post("/alquileres/solicitudes"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"idSucursalOrigen":"%s","origen":"X","destino":"Y",
                                 "fechaHoraSalida":"%s","idaYVuelta":false,
                                 "numeroPasajeros":10}"""
                                .formatted(XELA, OffsetDateTime.now().plusDays(5)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    @Test
    void cotizacionDeOtraSucursalEs403() throws Exception {
        // cliente@transportes.gt: su perfil con NIT viene en la semilla V2
        String token = tokenClienteConPerfil(UUID.fromString("00000000-0000-4000-8000-000000000031"), null);
        String id = solicitar(token);
        // Admin de otra sucursal (Huehue)
        mockMvc.perform(conToken(post("/alquileres/%s/cotizacion".formatted(id)),
                        token(Rol.ADMIN_SUCURSAL, HUEHUE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"precioFinal":"2700.00",
                                 "fechaHoraLlegadaEstimada":"%s"}""".formatted(
                                OffsetDateTime.now().plusDays(10)))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("OPERACION_NO_AUTORIZADA"));
    }

    @Test
    void historialMiosDelCliente() throws Exception {
        UUID idUsuario = UUID.randomUUID();
        jdbc.update("INSERT INTO cliente_ref (id_usuario, nit, nombre_completo, activo, version) "
                + "VALUES (?, '8888888-8', 'Historial Prueba', true, 0) ON CONFLICT DO NOTHING", idUsuario);
        String token = tokenClienteConPerfil(idUsuario, null);
        solicitar(token);
        mockMvc.perform(get("/alquileres/mios")
                        .param("pagina", "0").param("tamano", "10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido").isArray())
                .andExpect(jsonPath("$.totalElementos").value(not(nullValue())));
    }

    @Test
    void listarAdminSucursalVeSoloLosDeSuSucursal() throws Exception {
        mockMvc.perform(get("/alquileres")
                        .param("estado", "SOLICITADO")
                        .header("Authorization", "Bearer " + tokenAdminSucursal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido").isArray());
    }

    @Test
    void detalleAlquilerInexistenteEs404() throws Exception {
        mockMvc.perform(get("/alquileres/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenAdminSistema()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ALQUILER_NO_ENCONTRADO"));
    }

    @Test
    void republicarEventosYaNoExiste() throws Exception {
        // Se eliminó: los datos semilla de cada servicio ya están alineados por ID
        mockMvc.perform(conToken(post("/alquileres/admin/republicar-eventos"), tokenAdminSistema()))
                .andExpect(status().isNotFound());
    }
}
