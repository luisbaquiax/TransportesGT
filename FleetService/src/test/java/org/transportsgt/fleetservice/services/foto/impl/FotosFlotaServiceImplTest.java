package org.transportsgt.fleetservice.services.foto.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.transportsgt.eventos.payload.flota.EstadoOperativo;
import org.transportsgt.eventos.payload.identidad.Rol;
import org.transportsgt.fleetservice.almacenamiento.AlmacenamientoFotos;
import org.transportsgt.fleetservice.config.FotosProperties;
import org.transportsgt.fleetservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.fleetservice.exception.FotoInvalidaException;
import org.transportsgt.fleetservice.exception.FotoNoEncontradaException;
import org.transportsgt.fleetservice.exception.OperacionNoAutorizadaException;
import org.transportsgt.fleetservice.mappers.BusMapper;
import org.transportsgt.fleetservice.mappers.ChoferMapper;
import org.transportsgt.fleetservice.models.Bus;
import org.transportsgt.fleetservice.models.Chofer;
import org.transportsgt.fleetservice.repositories.BusRepository;
import org.transportsgt.fleetservice.repositories.ChoferRepository;
import org.transportsgt.fleetservice.repositories.SucursalRefRepository;
import org.transportsgt.fleetservice.services.autorizacion.AutorizacionSucursal;
import org.transportsgt.fleetservice.services.comun.BuscadorFlota;
import org.transportsgt.fleetservice.services.foto.FotoLeida;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FotosFlotaServiceImplTest {

    @Mock
    private AlmacenamientoFotos almacenamiento;
    @Mock
    private BusRepository busRepository;
    @Mock
    private ChoferRepository choferRepository;
    @Mock
    private SucursalRefRepository sucursalRefRepository;
    @Mock
    private BusMapper busMapper;
    @Mock
    private ChoferMapper choferMapper;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC);
    private final UUID idSucursal = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private final UUID id = UUID.fromString("b0000000-0000-4000-8000-000000000001");
    private final UsuarioAutenticado adminSucursal = new UsuarioAutenticado(UUID.randomUUID(),
            "admin.xela@transportes.gt", Rol.ADMIN_SUCURSAL, idSucursal, "Admin Xela");

    private FotosFlotaServiceImpl servicio;

    @BeforeEach
    void setUp() {
        FotosProperties propiedades = new FotosProperties("./fotos", 1024, new FotosProperties.Nube(false, ""));
        FormatosFoto formatos = new FormatosFoto();
        servicio = new FotosFlotaServiceImpl(almacenamiento, new ValidadorFoto(propiedades, formatos), formatos,
                new BuscadorFlota(busRepository, choferRepository), new AutorizacionSucursal(sucursalRefRepository),
                busRepository, choferRepository, busMapper, choferMapper, clock);
    }

    private MockMultipartFile archivo(String contentType, byte[] contenido) {
        return new MockMultipartFile("archivo", "foto.jpg", contentType, contenido);
    }

    private Bus bus(String fotoUrl) {
        Bus bus = Bus.builder().id(id).idSucursal(idSucursal).placa("P900ABC")
                .estadoOperativo(EstadoOperativo.DISPONIBLE).activo(true).version(1L).fotoUrl(fotoUrl).build();
        when(busRepository.findById(id)).thenReturn(Optional.of(bus));
        return bus;
    }

    // ---------- validación ----------

    @Test
    void guardarVacioLanza400() {
        bus(null);
        MockMultipartFile vacio = archivo("image/jpeg", new byte[0]);
        assertThrows(FotoInvalidaException.class, () -> servicio.guardarFotoBus(id, vacio, adminSucursal));
        verify(almacenamiento, never()).guardar(anyString(), any(), anyString());
    }

    @Test
    void guardarSinArchivoLanza400() {
        bus(null);
        assertThrows(FotoInvalidaException.class, () -> servicio.guardarFotoBus(id, null, adminSucursal));
    }

    @Test
    void guardarTamanoExcedidoLanza400() {
        bus(null);
        MockMultipartFile grande = archivo("image/jpeg", new byte[2048]);
        assertThrows(FotoInvalidaException.class, () -> servicio.guardarFotoBus(id, grande, adminSucursal));
    }

    @Test
    void guardarMimeNoPermitidoLanza400() {
        bus(null);
        MockMultipartFile gif = archivo("image/gif", new byte[]{1});
        assertThrows(FotoInvalidaException.class, () -> servicio.guardarFotoBus(id, gif, adminSucursal));
    }

    // ---------- guardar ----------

    @Test
    void guardarFotoBusDelegaConLaClaveYActualizaElBus() {
        Bus entity = bus(null);
        byte[] bytes = {1, 2, 3};
        when(busRepository.saveAndFlush(entity)).thenAnswer(inv -> inv.getArgument(0));

        servicio.guardarFotoBus(id, archivo("image/jpeg", bytes), adminSucursal);

        // foto_url guarda la clave, no una URL
        verify(almacenamiento).guardar(eq("buses/" + id + ".jpg"), eq(bytes), eq("image/jpeg"));
        assertEquals("buses/" + id + ".jpg", entity.getFotoUrl());
        // Fuerza una versión nueva para invalidar la caché del navegador, con el reloj del contexto
        assertEquals(OffsetDateTime.now(clock), entity.getActualizadoEn());
    }

    @Test
    void guardarFotoChoferNormalizaElContentType() {
        Chofer entity = Chofer.builder().id(id).idSucursal(idSucursal).activo(true).version(1L).build();
        when(choferRepository.findById(id)).thenReturn(Optional.of(entity));
        byte[] bytes = {9};
        when(choferRepository.saveAndFlush(entity)).thenAnswer(inv -> inv.getArgument(0));

        servicio.guardarFotoChofer(id, archivo("IMAGE/PNG", bytes), adminSucursal);

        verify(almacenamiento).guardar(eq("choferes/" + id + ".png"), eq(bytes), eq("image/png"));
        assertEquals("choferes/" + id + ".png", entity.getFotoUrl());
    }

    @Test
    void guardarFotoDeOtraSucursalLanza403() {
        bus(null);
        UsuarioAutenticado deOtra = new UsuarioAutenticado(UUID.randomUUID(), "x@transportes.gt",
                Rol.ADMIN_SUCURSAL, UUID.randomUUID(), "Otra");
        assertThrows(OperacionNoAutorizadaException.class,
                () -> servicio.guardarFotoBus(id, archivo("image/jpeg", new byte[]{1}), deOtra));
    }

    // ---------- leer ----------

    @Test
    void leerFotoBusSinFotoLanza404() {
        bus(null);
        assertThrows(FotoNoEncontradaException.class, () -> servicio.leerFotoBus(id, adminSucursal));
    }

    @Test
    void leerFotoBusConUrlAntiguaUsaSoloElNombre() throws Exception {
        bus("http://localhost:8082/v1/fleet/fotos/buses/" + id + ".png");
        byte[] bytes = {5, 6};
        when(almacenamiento.leer("buses/" + id + ".png")).thenReturn(Optional.of(new ByteArrayResource(bytes)));

        FotoLeida foto = servicio.leerFotoBus(id, adminSucursal);

        assertEquals(MediaType.IMAGE_PNG, foto.tipo());
        assertArrayEquals(bytes, foto.contenido().getContentAsByteArray());
    }

    @Test
    void leerFotoConNombreInseguroLanza404SinTocarAlmacenamiento() {
        bus("buses/..%2Fsecreto.jpg");
        assertThrows(FotoNoEncontradaException.class, () -> servicio.leerFotoBus(id, adminSucursal));
        verify(almacenamiento, never()).leer(anyString());
    }

    @Test
    void leerFotoChoferConClaveDevuelveElRecursoConSuTipo() {
        Chofer entity = Chofer.builder().id(id).idSucursal(idSucursal).activo(true).version(1L)
                .fotoUrl("choferes/" + id + ".webp").build();
        when(choferRepository.findById(id)).thenReturn(Optional.of(entity));
        when(almacenamiento.leer("choferes/" + id + ".webp")).thenReturn(Optional.of(new ByteArrayResource(new byte[]{1})));

        FotoLeida foto = servicio.leerFotoChofer(id, adminSucursal);

        assertEquals(MediaType.parseMediaType("image/webp"), foto.tipo());
    }
}
