package org.transportsgt.fleetservice.services.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.transportsgt.fleetservice.config.FotosProperties;
import org.transportsgt.fleetservice.exception.FotoInvalidaException;
import org.transportsgt.fleetservice.services.FotoService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Fotos en disco: {@code {directorio}/{buses|choferes}/{id}.{ext}} y URL pública
 * {@code {urlPublica}/{buses|choferes}/{id}.{ext}} (decisión de equipo, plan §5).
 */
@Slf4j
@Service
public class FotoServiceImpl implements FotoService {

    /** Nombres de archivo aceptados: nada de rutas ni caracteres raros. */
    private static final Pattern NOMBRE_SEGURO = Pattern.compile("^[A-Za-z0-9._-]{1,100}$");

    private static final Map<String, String> EXTENSIONES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private final FotosProperties propiedades;

    public FotoServiceImpl(FotosProperties propiedades) {
        this.propiedades = propiedades;
    }

    @Override
    public String guardar(Tipo tipo, UUID id, MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new FotoInvalidaException("Debe enviar una imagen en el campo 'archivo'");
        }
        if (archivo.getSize() > propiedades.tamanioMaximoBytes()) {
            throw new FotoInvalidaException("La imagen supera el tamaño máximo de "
                    + (propiedades.tamanioMaximoBytes() / (1024 * 1024)) + " MB");
        }
        String tipoContenido = archivo.getContentType() == null ? "" : archivo.getContentType().toLowerCase(Locale.ROOT);
        String extension = EXTENSIONES.get(tipoContenido);
        if (extension == null) {
            throw new FotoInvalidaException("Formato no permitido: use JPEG, PNG o WebP");
        }
        String carpeta = carpeta(tipo);
        Path directorio = Path.of(propiedades.directorio(), carpeta);
        String nombre = id + "." + extension;
        try {
            Files.createDirectories(directorio);
            Path destino = directorio.resolve(nombre);
            // Reemplaza la foto anterior (y borra extensiones viejas si cambiaron de formato)
            borrarAnteriores(directorio, id);
            Files.copy(archivo.getInputStream(), destino);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la imagen", e);
        }
        log.debug("Foto guardada en {}", directorio.resolve(nombre));
        return propiedades.urlPublica() + "/" + carpeta + "/" + nombre;
    }

    @Override
    public Optional<Resource> leer(Tipo tipo, String nombre) {
        if (nombre == null || !NOMBRE_SEGURO.matcher(nombre).matches()) {
            return Optional.empty();
        }
        Path ruta = Path.of(propiedades.directorio(), carpeta(tipo), nombre).normalize();
        if (!ruta.startsWith(Path.of(propiedades.directorio(), carpeta(tipo)).normalize())
                || !Files.isRegularFile(ruta)) {
            return Optional.empty();
        }
        return Optional.of(new FileSystemResource(ruta));
    }

    @Override
    public void borrar(Tipo tipo, UUID id) {
        Path directorio = Path.of(propiedades.directorio(), carpeta(tipo));
        if (Files.isDirectory(directorio)) {
            try {
                borrarAnteriores(directorio, id);
            } catch (IOException e) {
                log.warn("No se pudieron borrar las fotos anteriores de {}", id, e);
            }
        }
    }

    private void borrarAnteriores(Path directorio, UUID id) throws IOException {
        try (var archivos = Files.list(directorio)) {
            archivos.filter(p -> p.getFileName().toString().startsWith(id.toString() + "."))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            log.warn("No se pudo borrar la foto anterior {}", p, e);
                        }
                    });
        }
    }

    private static String carpeta(Tipo tipo) {
        return tipo.name().toLowerCase(Locale.ROOT);
    }
}
