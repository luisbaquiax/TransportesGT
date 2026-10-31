package org.transportsgt.fleetservice.almacenamiento;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.transportsgt.fleetservice.config.FotosProperties;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Almacenamiento local
 *
 * <p>Es la implementación activa mientras {@code FOTOS_NUBE_HABILITADA} no sea {@code true}.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "flota.fotos.nube", name = "habilitada", havingValue = "false",
        matchIfMissing = true)
public class AlmacenamientoFotosLocal implements AlmacenamientoFotos {

    private final FotosProperties propiedades;

    public AlmacenamientoFotosLocal(FotosProperties propiedades) {
        this.propiedades = propiedades;
    }

    @Override
    public void guardar(String clave, byte[] contenido, String contentType) {
        Path destino = ruta(clave);
        try {
            Files.createDirectories(destino.getParent());
            // Borra también las extensiones anteriores (png/webp → jpg) al reemplazar
            borrarPorPrefijo(baseSinExtension(clave));
            Files.write(destino, contenido);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la imagen", e);
        }
        log.debug("Foto guardada en {}", destino);
    }

    @Override
    public Optional<Resource> leer(String clave) {
        Path ruta = ruta(clave);
        if (!Files.isRegularFile(ruta)) {
            return Optional.empty();
        }
        return Optional.of(new FileSystemResource(ruta));
    }

    @Override
    public void borrar(String clave) {
        try {
            Files.deleteIfExists(ruta(clave));
        } catch (IOException e) {
            log.warn("No se pudo borrar la foto {}", clave, e);
        }
    }

    @Override
    public void borrarPorPrefijo(String prefijo) {
        Path directorio = ruta(prefijo).getParent();
        if (directorio == null || !Files.isDirectory(directorio)) {
            return;
        }
        String nombreBase = nombreBaseDe(prefijo);
        try (var archivos = Files.list(directorio)) {
            archivos.filter(p -> p.getFileName().toString().startsWith(nombreBase))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            log.warn("No se pudo borrar la foto anterior {}", p, e);
                        }
                    });
        } catch (IOException e) {
            log.warn("No se pudo listar {}", directorio, e);
        }
    }

    /**
     * Ruta absoluta, normalizada y siempre dentro del directorio configurado.
     */
    private Path ruta(String clave) {
        Path base = Path.of(propiedades.directorio()).toAbsolutePath().normalize();
        Path ruta = base.resolve(clave).normalize();
        if (!ruta.startsWith(base)) {
            throw new IllegalArgumentException("Clave de foto fuera del directorio permitido: " + clave);
        }
        return ruta;
    }

    /**
     * {@code buses/1-2-3.jpg} → {@code buses/1-2-3.} (prefijo que cubre todas las extensiones).
     */
    private String baseSinExtension(String clave) {
        int punto = clave.lastIndexOf('.');
        return punto < 0 ? clave + "." : clave.substring(0, punto + 1);
    }

    private String nombreBaseDe(String prefijo) {
        int barra = prefijo.lastIndexOf('/');
        return prefijo.substring(barra + 1);
    }
}
