package org.transportsgt.fleetservice.almacenamiento;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.transportsgt.fleetservice.config.FotosProperties;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Almacenamiento en Google Cloud Storage.
 *
 * <p>Se activa <b>solo</b> con variables de entorno, sin tocar código:
 * </p>
 * <pre>
 * FOTOS_NUBE_HABILITADA=true
 * FOTOS_NUBE_BUCKET=tu-bucket
 * GOOGLE_APPLICATION_CREDENTIALS=/ruta/sa.json   # en Cloud Run no hace falta: identidad de la plataforma
 * </pre>
 *
 * <p>Credenciales: <i>Application Default Credentials</i> (variable anterior o cuenta de
 * servicio de la plataforma). Los objetos son privados: el bucket no debe dar lectura a
 * {@code allUsers}; el servicio los lee con su cuenta y los sirve con la autorización del registro.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "flota.fotos.nube", name = "habilitada", havingValue = "true")
public class AlmacenamientoFotosNube implements AlmacenamientoFotos {

    private final FotosProperties propiedades;
    private final Supplier<Storage> proveedor;
    private volatile Storage storage;

    @Autowired
    public AlmacenamientoFotosNube(FotosProperties propiedades) {
        this(propiedades, () -> StorageOptions.getDefaultInstance().getService());
    }

    /**
     * Constructor para pruebas: permite sustituir el cliente de Cloud Storage.
     */
    AlmacenamientoFotosNube(FotosProperties propiedades, Supplier<Storage> proveedor) {
        this.propiedades = propiedades;
        this.proveedor = proveedor;
        if (propiedades.nube().bucket() == null || propiedades.nube().bucket().isBlank()) {
            throw new IllegalStateException(
                    "FOTOS_NUBE_HABILITADA=true pero falta FOTOS_NUBE_BUCKET "
                            + "(nombre del bucket de Cloud Storage)");
        }
    }

    @Override
    public void guardar(String clave, byte[] contenido, String contentType) {
        // Borra también las extensiones anteriores al reemplazar (png/webp → jpg)
        borrarPorPrefijo(baseSinExtension(clave));
        BlobInfo info = BlobInfo.newBuilder(bucket(), clave).setContentType(contentType).build();
        // Objeto privado (sin ACL pública): solo lo lee la cuenta de servicio y se sirve por
        // GET /buses/{id}/foto o /choferes/{id}/foto con la autorización del registro.
        storage().create(info, contenido);
        log.debug("Foto subida al bucket {} como {}", bucket(), clave);
    }

    @Override
    public Optional<Resource> leer(String clave) {
        try {
            Blob blob = storage().get(BlobId.of(bucket(), clave));
            if (blob == null || !blob.exists()) {
                return Optional.empty();
            }
            return Optional.of(new ByteArrayResource(blob.getContent()));
        } catch (StorageException e) {
            log.warn("No se pudo leer {} del bucket {}: {}", clave, bucket(), e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void borrar(String clave) {
        try {
            storage().delete(BlobId.of(bucket(), clave));
        } catch (StorageException e) {
            log.warn("No se pudo borrar {} del bucket {}: {}", clave, bucket(), e.getMessage());
        }
    }

    @Override
    public void borrarPorPrefijo(String prefijo) {
        try {
            storage().list(bucket(), Storage.BlobListOption.prefix(prefijo))
                    .iterateAll()
                    .forEach(blob -> storage().delete(blob.getBlobId()));
        } catch (StorageException e) {
            log.warn("No se pudieron borrar objetos con prefijo {} en {}: {}", prefijo, bucket(),
                    e.getMessage());
        }
    }

    private String bucket() {
        return propiedades.nube().bucket();
    }

    private String baseSinExtension(String clave) {
        int punto = clave.lastIndexOf('.');
        return punto < 0 ? clave + "." : clave.substring(0, punto + 1);
    }

    /**
     * Cliente HTTP a Cloud Storage; se crea al primer uso para no frenar el arranque.
     */
    private Storage storage() {
        Storage actual = storage;
        if (actual == null) {
            synchronized (this) {
                if (storage == null) {
                    storage = proveedor.get();
                }
                actual = storage;
            }
        }
        return actual;
    }
}
