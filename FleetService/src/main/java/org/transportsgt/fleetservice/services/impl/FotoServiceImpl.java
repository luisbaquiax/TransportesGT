package org.transportsgt.fleetservice.services.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.transportsgt.fleetservice.almacenamiento.AlmacenamientoFotos;
import org.transportsgt.fleetservice.config.FotosProperties;
import org.transportsgt.fleetservice.exception.FotoInvalidaException;
import org.transportsgt.fleetservice.services.FotoService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Valida la imagen y delega el guardado en el {@link AlmacenamientoFotos} activo:
 * disco local en desarrollo ({@code ./public/fotos}) o Cloud Storage cuando
 * {@code FOTOS_NUBE_HABILITADA=true} (plan §5, decisión 1).
 *
 * <p>La clave siempre es {@code {buses|choferes}/{id}.{ext}} y la URL pública
 * ({@code foto_url}) la devuelve el almacenamiento.
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
    private final AlmacenamientoFotos almacenamiento;

    public FotoServiceImpl(FotosProperties propiedades, AlmacenamientoFotos almacenamiento) {
        this.propiedades = propiedades;
        this.almacenamiento = almacenamiento;
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
        String clave = carpeta(tipo) + "/" + id + "." + extension;
        byte[] contenido;
        try {
            contenido = archivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la imagen enviada", e);
        }
        return almacenamiento.guardar(clave, contenido, tipoContenido);
    }

    @Override
    public Optional<Resource> leer(Tipo tipo, String nombre) {
        if (nombre == null || !NOMBRE_SEGURO.matcher(nombre).matches()) {
            return Optional.empty();
        }
        return almacenamiento.leer(carpeta(tipo) + "/" + nombre);
    }

    @Override
    public void borrar(Tipo tipo, UUID id) {
        almacenamiento.borrarPorPrefijo(carpeta(tipo) + "/" + id + ".");
    }

    private static String carpeta(Tipo tipo) {
        return tipo.name().toLowerCase(Locale.ROOT);
    }
}
