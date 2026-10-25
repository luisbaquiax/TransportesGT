package org.transportsgt.ticketservice.documentos;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * Imagen PNG de un código QR. En los boletos el contenido es el código del boleto ({@code BOL-…}).
 */
@Component
public class GeneradorQr {

    private static final Map<EncodeHintType, Object> OPCIONES = Map.of(
            EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN, 1);

    private final QRCodeWriter escritor = new QRCodeWriter();

    public byte[] png(String contenido, int lado) {
        try {
            BitMatrix matriz = escritor.encode(contenido, BarcodeFormat.QR_CODE, lado, lado, OPCIONES);
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matriz, "PNG", salida);
            return salida.toByteArray();
        } catch (WriterException e) {
            throw new IllegalArgumentException("No se pudo generar el QR de " + contenido, e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
