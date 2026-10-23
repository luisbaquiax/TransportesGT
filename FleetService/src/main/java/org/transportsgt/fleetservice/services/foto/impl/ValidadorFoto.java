package org.transportsgt.fleetservice.services.foto.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.transportsgt.fleetservice.config.FotosProperties;
import org.transportsgt.fleetservice.exception.FotoInvalidaException;

/**
 * Imagen presente, de JPEG, PNG o WebP y dentro del tamaño máximo ({@code FOTOS_TAMANIO_MAXIMO_BYTES}).
 */
@Component
@RequiredArgsConstructor
class ValidadorFoto {

    private final FotosProperties propiedades;
    private final FormatosFoto formatos;

    /**
     * @return la extensión con que se guardará
     */
    String validar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new FotoInvalidaException("Debe enviar una imagen en el campo 'archivo'");
        }
        if (archivo.getSize() > propiedades.tamanioMaximoBytes()) {
            throw new FotoInvalidaException("La imagen supera el tamaño máximo de "
                    + (propiedades.tamanioMaximoBytes() / (1024 * 1024)) + " MB");
        }
        return formatos.extensionDe(archivo.getContentType())
                .orElseThrow(() -> new FotoInvalidaException("Formato no permitido: use JPEG, PNG o WebP"));
    }
}
