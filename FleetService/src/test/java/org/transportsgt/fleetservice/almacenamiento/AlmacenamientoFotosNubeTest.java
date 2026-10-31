package org.transportsgt.fleetservice.almacenamiento;

import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.api.gax.paging.Page;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.Resource;
import org.transportsgt.fleetservice.config.FotosProperties;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlmacenamientoFotosNubeTest {

    private static final String CLAVE = "buses/abc.jpg";
    private static final String BUCKET = "bucket-prueba";

    private static FotosProperties propiedades() {
        return new FotosProperties("./fotos", 5_000_000, new FotosProperties.Nube(true, BUCKET));
    }

    private static StorageException prohibido() {
        return new StorageException(403, "ACL no permitida");
    }

    @SuppressWarnings("unchecked")
    private static Page<Blob> paginaVacia() {
        return mock(Page.class);
    }

    @Test
    void sinBucketNoArranca() {
        FotosProperties sinBucket = new FotosProperties("./fotos", 5_000_000, new FotosProperties.Nube(true, ""));
        assertThrows(IllegalStateException.class, () -> new AlmacenamientoFotosNube(sinBucket));
    }

    @Test
    void guardarSubeElObjetoPrivadoSinAcl() {
        Storage storage = mock(Storage.class);
        Page<Blob> vacia = paginaVacia();
        when(vacia.iterateAll()).thenReturn(List.of());
        when(storage.list(anyString(), any(Storage.BlobListOption.class))).thenReturn(vacia);
        AlmacenamientoFotosNube almacenamiento =
                new AlmacenamientoFotosNube(propiedades(), () -> storage);

        almacenamiento.guardar(CLAVE, new byte[]{1}, "image/jpeg");

        ArgumentCaptor<BlobInfo> info = ArgumentCaptor.forClass(BlobInfo.class);
        verify(storage).create(info.capture(), eq(new byte[]{1}));
        assertEquals(BlobId.of(BUCKET, CLAVE), info.getValue().getBlobId());
    }

    @Test
    void guardarNoPideLecturaPublica() {
        Storage storage = mock(Storage.class);
        Page<Blob> vacia = paginaVacia();
        when(vacia.iterateAll()).thenReturn(List.of());
        when(storage.list(anyString(), any(Storage.BlobListOption.class))).thenReturn(vacia);
        AlmacenamientoFotosNube almacenamiento =
                new AlmacenamientoFotosNube(propiedades(), () -> storage);

        almacenamiento.guardar(CLAVE, new byte[]{2}, "image/jpeg");

        verify(storage, never()).create(any(BlobInfo.class), any(byte[].class), any(Storage.BlobTargetOption.class));
    }

    @Test
    void leerDevuelveElContenido() {
        Storage storage = mock(Storage.class);
        Blob blob = mock(Blob.class);
        when(blob.exists()).thenReturn(true);
        when(blob.getContent()).thenReturn(new byte[]{5, 6});
        when(storage.get(BlobId.of(BUCKET, CLAVE))).thenReturn(blob);
        AlmacenamientoFotosNube almacenamiento =
                new AlmacenamientoFotosNube(propiedades(), () -> storage);

        Optional<Resource> recurso = almacenamiento.leer(CLAVE);

        assertTrue(recurso.isPresent());
        try {
            assertArrayEquals(new byte[]{5, 6}, recurso.get().getContentAsByteArray());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void leerBlobInexistenteOVacioDevuelveVacio() {
        Storage storage = mock(Storage.class);
        when(storage.get(any(BlobId.class))).thenReturn(null);
        AlmacenamientoFotosNube almacenamiento =
                new AlmacenamientoFotosNube(propiedades(), () -> storage);
        assertTrue(almacenamiento.leer(CLAVE).isEmpty());

        Blob blob = mock(Blob.class);
        when(blob.exists()).thenReturn(false);
        when(storage.get(BlobId.of(BUCKET, CLAVE))).thenReturn(blob);
        assertTrue(almacenamiento.leer(CLAVE).isEmpty());
    }

    @Test
    void leerErrorDeNubeDevuelveVacioNoExplota() {
        Storage storage = mock(Storage.class);
        when(storage.get(any(BlobId.class))).thenThrow(new StorageException(500, "nube caída"));
        AlmacenamientoFotosNube almacenamiento =
                new AlmacenamientoFotosNube(propiedades(), () -> storage);
        assertTrue(almacenamiento.leer(CLAVE).isEmpty());
    }

    @Test
    void borrarNoFallaSiLaNubeRespondeMal() {
        Storage storage = mock(Storage.class);
        when(storage.delete(any(BlobId.class))).thenThrow(new StorageException(500, "nube caída"));
        AlmacenamientoFotosNube almacenamiento =
                new AlmacenamientoFotosNube(propiedades(), () -> storage);
        almacenamiento.borrar(CLAVE);
    }

    @Test
    void borrarPorPrefijoEliminaTodosLosObjetos() {
        Storage storage = mock(Storage.class);
        Blob blob = mock(Blob.class);
        when(blob.getBlobId()).thenReturn(BlobId.of(BUCKET, CLAVE));
        Page<Blob> pagina = mock(Page.class);
        when(pagina.iterateAll()).thenReturn(List.of(blob));
        when(storage.list(eq(BUCKET), any(Storage.BlobListOption.class))).thenReturn(pagina);
        AlmacenamientoFotosNube almacenamiento =
                new AlmacenamientoFotosNube(propiedades(), () -> storage);

        almacenamiento.borrarPorPrefijo("buses/abc.");

        verify(storage).delete(BlobId.of(BUCKET, CLAVE));
    }

    @Test
    void borrarPorPrefijoErrorDeNubeNoExplota() {
        Storage storage = mock(Storage.class);
        when(storage.list(anyString(), any(Storage.BlobListOption.class)))
                .thenThrow(new StorageException(500, "nube caída"));
        AlmacenamientoFotosNube almacenamiento =
                new AlmacenamientoFotosNube(propiedades(), () -> storage);
        almacenamiento.borrarPorPrefijo("buses/abc.");
        verify(storage, never()).delete(any(BlobId.class));
    }
}
