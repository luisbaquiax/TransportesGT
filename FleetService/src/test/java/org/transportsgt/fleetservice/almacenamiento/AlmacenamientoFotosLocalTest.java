package org.transportsgt.fleetservice.almacenamiento;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.transportsgt.fleetservice.config.FotosProperties;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlmacenamientoFotosLocalTest {

    @TempDir
    Path directorio;

    private AlmacenamientoFotosLocal almacenamiento() {
        return new AlmacenamientoFotosLocal(new FotosProperties(
                directorio.toString(), "http://localhost/fotos", 5_000_000, false, ""));
    }

    @Test
    void guardarCreaElArchivoYDevuelveUrl() throws Exception {
        byte[] bytes = {1, 2, 3, 4};
        String url = almacenamiento().guardar("buses/abc.jpg", bytes, "image/jpeg");

        assertEquals("http://localhost/fotos/buses/abc.jpg", url);
        assertArrayEquals(bytes, Files.readAllBytes(directorio.resolve("buses/abc.jpg")));
    }

    @Test
    void guardarReemplazaLaExtensionAnterior() throws Exception {
        AlmacenamientoFotosLocal almacenamiento = almacenamiento();
        almacenamiento.guardar("buses/abc.png", new byte[]{9}, "image/png");
        almacenamiento.guardar("buses/abc.jpg", new byte[]{1}, "image/jpeg");

        assertTrue(Files.exists(directorio.resolve("buses/abc.jpg")));
        assertFalse(Files.exists(directorio.resolve("buses/abc.png")), "el png anterior debe borrarse");
    }

    @Test
    void leerDevuelveElRecursoOVacio() throws Exception {
        AlmacenamientoFotosLocal almacenamiento = almacenamiento();
        almacenamiento.guardar("choferes/c1.jpg", new byte[]{7}, "image/jpeg");

        Optional<Resource> encontrado = almacenamiento.leer("choferes/c1.jpg");
        assertTrue(encontrado.isPresent());
        assertArrayEquals(new byte[]{7}, encontrado.get().getContentAsByteArray());

        assertTrue(almacenamiento.leer("choferes/no-existe.jpg").isEmpty());
    }

    @Test
    void borrarNoFallaSiNoExiste() {
        almacenamiento().borrar("buses/no-existe.jpg");
    }

    @Test
    void borrarBorraElArchivo() throws Exception {
        AlmacenamientoFotosLocal almacenamiento = almacenamiento();
        almacenamiento.guardar("buses/x.jpg", new byte[]{1}, "image/jpeg");
        almacenamiento.borrar("buses/x.jpg");
        assertFalse(Files.exists(directorio.resolve("buses/x.jpg")));
    }

    @Test
    void borrarPorPrefijoEliminaTodasLasExtensiones() throws Exception {
        AlmacenamientoFotosLocal almacenamiento = almacenamiento();
        almacenamiento.guardar("buses/1.jpg", new byte[]{1}, "image/jpeg");
        Files.write(directorio.resolve("buses/1.png"), new byte[]{2});
        Files.write(directorio.resolve("buses/2.jpg"), new byte[]{3});

        almacenamiento.borrarPorPrefijo("buses/1.");

        assertFalse(Files.exists(directorio.resolve("buses/1.jpg")));
        assertFalse(Files.exists(directorio.resolve("buses/1.png")));
        assertTrue(Files.exists(directorio.resolve("buses/2.jpg")), "otras fotos no se tocan");
    }

    @Test
    void borrarPorPrefijoSinDirectorioNoFalla() {
        almacenamiento().borrarPorPrefijo("no-existe/1.");
    }

    @Test
    void rechazaClavesFueraDelDirectorio() {
        AlmacenamientoFotosLocal almacenamiento = almacenamiento();
        assertThrows(IllegalArgumentException.class, () -> almacenamiento.leer("../secreto.jpg"));
        assertThrows(IllegalArgumentException.class, () -> almacenamiento.guardar("../../x.jpg", new byte[]{1}, "image/jpeg"));
        assertThrows(IllegalArgumentException.class, () -> almacenamiento.borrar("buses/../../x.jpg"));
    }
}
