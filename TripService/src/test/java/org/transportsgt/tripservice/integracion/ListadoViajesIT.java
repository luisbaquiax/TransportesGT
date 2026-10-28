package org.transportsgt.tripservice.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.transportsgt.eventos.payload.identidad.Rol;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /viajes filtrado y paginado en la base de datos: cada filtro, los bordes inclusivos de fecha, la visibilidad
 * por rol (la misma de antes, ahora en ViajeSpecs) y la paginación estable por hora de salida.
 * Cada prueba usa su propia ruta para no depender de los viajes que crean las demás pruebas.
 */
class ListadoViajesIT extends IntegracionBase {

    /** Desplazamiento en días distinto por prueba para no traslapar buses ni choferes. */
    private static final AtomicInteger DIAS = new AtomicInteger(200);

    private record Escenario(String idRuta, String t1, String t2, String t3, OffsetDateTime s1, OffsetDateTime s2,
                             OffsetDateTime s3) {
    }

    private String rutaNueva() throws Exception {
        UUID destino = UUID.randomUUID();
        jdbc.update("INSERT INTO sucursal_ref (id, nombre, activa, version) VALUES (?, ?, true, 0)",
                destino, "Destino " + destino.toString().substring(0, 8));
        return leer(mockMvc.perform(conToken(post("/rutas"), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"idSucursalOrigen":"%s","idSucursalDestino":"%s",
                                 "distanciaKm":"50.00","precioBoleto":"25.00"}""".formatted(XELA, destino))))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asString();
    }

    private String viaje(String idRuta, UUID bus, UUID chofer, OffsetDateTime salida) throws Exception {
        return leer(mockMvc.perform(conToken(post("/viajes"), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"idRuta":"%s","idBus":"%s","idChofer":"%s",
                                 "fechaHoraSalida":"%s","fechaHoraLlegadaEstimada":"%s"}"""
                                .formatted(idRuta, bus, chofer, salida, salida.plusHours(3)))))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asString();
    }

    /** Tres viajes en una ruta propia: t1 y t3 con el chofer 1, t2 con el chofer 2, en días consecutivos. */
    private Escenario escenario() throws Exception {
        OffsetDateTime base = OffsetDateTime.now(ZoneOffset.UTC).plusDays(DIAS.getAndAdd(10))
                .truncatedTo(ChronoUnit.HOURS);
        String ruta = rutaNueva();
        OffsetDateTime s1 = base;
        OffsetDateTime s2 = base.plusDays(1);
        OffsetDateTime s3 = base.plusDays(2);
        // Se crean desordenados: el listado debe ordenarlos por salida
        String t3 = viaje(ruta, BUS_2, CHOFER_1, s3);
        String t1 = viaje(ruta, BUS_1, CHOFER_1, s1);
        String t2 = viaje(ruta, BUS_1, CHOFER_2, s2);
        return new Escenario(ruta, t1, t2, t3, s1, s2, s3);
    }

    @Test
    void paginaYOrdenaPorSalida() throws Exception {
        Escenario e = escenario();

        mockMvc.perform(conToken(get("/viajes").param("idRuta", e.idRuta()).param("tamano", "2"), tokenAdminSistema()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamano").value(2))
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.contenido[0].id").value(e.t1()))
                .andExpect(jsonPath("$.contenido[1].id").value(e.t2()))
                // La respuesta trae bus y chofer armados (carga en lote)
                .andExpect(jsonPath("$.contenido[0].bus.placa").value("P123ABC"))
                .andExpect(jsonPath("$.contenido[0].chofer.nombreCompleto").value("Juan Pérez"));
        mockMvc.perform(conToken(get("/viajes").param("idRuta", e.idRuta()).param("tamano", "2").param("pagina", "1"),
                        tokenAdminSistema()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(e.t3()));
    }

    @Test
    void desdeYHastaSonInclusivos() throws Exception {
        Escenario e = escenario();

        mockMvc.perform(conToken(get("/viajes").param("idRuta", e.idRuta())
                        .param("desde", e.s2().toString()).param("hasta", e.s3().toString()), tokenAdminSistema()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[0].id").value(e.t2()))
                .andExpect(jsonPath("$.contenido[1].id").value(e.t3()));
        mockMvc.perform(conToken(get("/viajes").param("idRuta", e.idRuta())
                        .param("hasta", e.s1().toString()), tokenAdminSistema()))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(e.t1()));
    }

    @Test
    void visibilidadPorRol() throws Exception {
        Escenario e = escenario();

        // ADMIN_SISTEMA: todo; con idSucursal de otra sucursal, nada
        mockMvc.perform(conToken(get("/viajes").param("idRuta", e.idRuta()), tokenAdminSistema()))
                .andExpect(jsonPath("$.totalElementos").value(3));
        mockMvc.perform(conToken(get("/viajes").param("idRuta", e.idRuta()).param("idSucursal", CAPITAL.toString()),
                        tokenAdminSistema()))
                .andExpect(jsonPath("$.totalElementos").value(0));
        // SUC y CAJ de la sucursal sí; de otra sucursal, nada (aunque pidan la ruta)
        mockMvc.perform(conToken(get("/viajes").param("idRuta", e.idRuta()), tokenAdminSucursal()))
                .andExpect(jsonPath("$.totalElementos").value(3));
        mockMvc.perform(conToken(get("/viajes").param("idRuta", e.idRuta()), tokenCajero()))
                .andExpect(jsonPath("$.totalElementos").value(3));
        mockMvc.perform(conToken(get("/viajes").param("idRuta", e.idRuta()), token(Rol.CAJERO, CAPITAL, null)))
                .andExpect(jsonPath("$.totalElementos").value(0));
        mockMvc.perform(conToken(get("/viajes").param("idRuta", e.idRuta()), token(Rol.ADMIN_SUCURSAL, CAPITAL, null)))
                .andExpect(jsonPath("$.totalElementos").value(0));
        // Chofer: solo los viajes en que es el chofer asignado
        mockMvc.perform(conToken(get("/viajes").param("idRuta", e.idRuta()), tokenChofer1()))
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[0].id").value(e.t1()))
                .andExpect(jsonPath("$.contenido[1].id").value(e.t3()));
        mockMvc.perform(conToken(get("/viajes").param("idRuta", e.idRuta()),
                        token(Rol.CHOFER, XELA, UUID.fromString("e0000000-0000-4000-8000-000000000002"))))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(e.t2()));
        // Chofer sin chofer_ref enlazado
        mockMvc.perform(conToken(get("/viajes").param("idRuta", e.idRuta()), token(Rol.CHOFER, XELA, UUID.randomUUID())))
                .andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void filtraPorTipoEstadoYPendientes() throws Exception {
        Escenario e = escenario();

        mockMvc.perform(conToken(get("/viajes").param("idRuta", e.idRuta()).param("tipoViaje", "REGULAR"),
                        tokenAdminSistema()))
                .andExpect(jsonPath("$.totalElementos").value(3));
        mockMvc.perform(conToken(get("/viajes").param("idRuta", e.idRuta()).param("tipoViaje", "ALQUILER_PRIVADO"),
                        tokenAdminSistema()))
                .andExpect(jsonPath("$.totalElementos").value(0));
        mockMvc.perform(conToken(get("/viajes").param("idRuta", e.idRuta()).param("estado", "EN_TRANSITO"),
                        tokenAdminSistema()))
                .andExpect(jsonPath("$.totalElementos").value(0));
        mockMvc.perform(conToken(post("/viajes/%s/salida".formatted(e.t2())), tokenAdminSucursal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"fechaHoraSalidaReal":"%s","kilometrajeInicial":5000}""".formatted(e.s2()))))
                .andExpect(status().isOk());
        mockMvc.perform(conToken(get("/viajes").param("idRuta", e.idRuta()).param("estado", "EN_TRANSITO"),
                        tokenAdminSistema()))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(e.t2()))
                .andExpect(jsonPath("$.contenido[0].registroSalida.kilometrajeInicial").value(5000));

        // sinAsignar: solo alquileres PENDIENTE_ASIGNACION (incluye el de la semilla)
        mockMvc.perform(conToken(get("/viajes").param("sinAsignar", "true").param("tamano", "100"), tokenAdminSistema()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[*].id", hasItem("50000000-0000-4000-8000-000000000003")))
                .andExpect(jsonPath("$.contenido[*].estado", everyItem(is("PENDIENTE_ASIGNACION"))));
    }

    @Test
    void paginacionInvalidaResponde400() throws Exception {
        mockMvc.perform(conToken(get("/viajes").param("tamano", "101"), tokenAdminSistema()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(conToken(get("/viajes").param("tamano", "0"), tokenAdminSistema()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(conToken(get("/viajes").param("pagina", "-1"), tokenAdminSistema()))
                .andExpect(status().isBadRequest());
    }
}
