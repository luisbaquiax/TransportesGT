package org.transportsgt.fleetservice.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.transportsgt.fleetservice.almacenamiento.AlmacenamientoFotos;
import org.transportsgt.fleetservice.config.FotosProperties;
import org.transportsgt.fleetservice.exception.FotoInvalidaException;
import org.transportsgt.fleetservice.services.FotoService;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FotoServiceImplTest {

    @Mock
    private AlmacenamientoFotos almacenamiento;

    private FotoServiceImpl servicio;

    private final UUID id = UUID.fromString("b0000000-0000-4000-8000-000000000001");

    @BeforeEach
    void setUp() {
        FotosProperties propiedades = new FotosProperties("./fotos", "http://localhost/fotos",
                1024, false, "");
        servicio = new FotoServiceImpl(propiedades, almacenamiento);
    }

    private static MockMultipartFile archivo(String contentType, byte[] contenido) {
        return new MockMultipartFile("archivo", "foto.jpg", contentType, contenido);
    }

    @Test
    void guardarVacioLanza400() {
        MockMultipartFile vacio = new MockMultipartFile("archivo", "foto.jpg", "image/jpeg", new byte[0]);
        assertThrows(FotoInvalidaException.class, () -> servicio.guardar(FotoService.Tipo.BUSES, id, vacio));
    }

    @Test
    void guardarSinArchivoLanza400() {
        assertThrows(FotoInvalidaException.class, () -> servicio.guardar(FotoService.Tipo.BUSES, id, null));
    }

    @Test
    void guardarTamanoExcedidoLanza400() {
        MockMultipartFile grande = archivo("image/jpeg", new byte[2048]);
        assertThrows(FotoInvalidaException.class, () -> servicio.guardar(FotoService.Tipo.BUSES, id, grande));
    }

    @Test
    void guardarMimeNoPermitidoLanza400() {
        MockMultipartFile gif = archivo("image/gif", new byte[]{1});
        assertThrows(FotoInvalidaException.class, () -> servicio.guardar(FotoService.Tipo.BUSES, id, gif));
    }

    @Test
    void guardarDelegaEnAlmacenamientoConClaveEsperada() {
        byte[] bytes = {1, 2, 3};
        when(almacenamiento.guardar(eq("buses/" + id + ".jpg"), eq(bytes), eq("image/jpeg")))
                .thenReturn("http://localhost/fotos/buses/" + id + ".jpg");

        String url = servicio.guardar(FotoService.Tipo.BUSES, id, archivo("image/jpeg", bytes));

        assertEquals("http://localhost/fotos/buses/" + id + ".jpg", url);
    }

    @Test
    void guardarMayusculasDeContentTypeSeNormaliza() {
        byte[] bytes = {9};
        when(almacenamiento.guardar(eq("choferes/" + id + ".png"), eq(bytes), eq("image/png")))
                .thenReturn("ok");
        assertEquals("ok", servicio.guardar(FotoService.Tipo.CHOFERES, id, archivo("IMAGE/PNG", bytes)));
    }

    @Test
    void leerNombreInseguroDevuelveVacioSinTocarAlmacenamiento() {
        org.junit.jupiter.api.Assertions.assertTrue(
                servicio.leer(FotoService.Tipo.BUSES, "../secreto.jpg").isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(
                servicio.leer(FotoService.Tipo.BUSES, null).isEmpty());
    }

    @Test
    void leerDevuelveElRecurso() {
        byte[] bytes = {5, 6};
        when(almacenamiento.leer("buses/" + id + ".jpg"))
                .thenReturn(Optional.of(new ByteArrayResource(bytes)));

        Optional<Resource> recurso = servicio.leer(FotoService.Tipo.BUSES, id + ".jpg");

        org.junit.jupiter.api.Assertions.assertTrue(recurso.isPresent());
        try {
            assertArrayEquals(bytes, recurso.get().getContentAsByteArray());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void borrarUsaPrefijoParaEliminarVariantes() {
        servicio.borrar(FotoService.Tipo.BUSES, id);
        verify(almacenamiento).borrarPorPrefijo("buses/" + id + ".");
    }
}
