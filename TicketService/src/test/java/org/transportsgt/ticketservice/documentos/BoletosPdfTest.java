package org.transportsgt.ticketservice.documentos;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.transportsgt.ticketservice.fixture.CompraFixture;
import org.transportsgt.ticketservice.fixture.ViajeRefFixture;
import org.transportsgt.ticketservice.models.Compra;
import org.transportsgt.ticketservice.models.ViajeRef;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoletosPdfTest {

    private final GeneradorQr generadorQr = new GeneradorQr();

    @Test
    void png_seLeeDeVueltaComoElCodigoDelBoleto() throws Exception {
        // Act
        byte[] png = generadorQr.png("BOL-7K3MPQ2A", 300);

        // Assert
        var imagen = ImageIO.read(new ByteArrayInputStream(png));
        var bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(imagen)));
        assertEquals("BOL-7K3MPQ2A", new QRCodeReader().decode(bitmap).getText());
    }

    @Test
    void generar_incluyeElViajeElCompradorYCadaCodigo() throws Exception {
        // Arrange
        ViajeRef viaje = ViajeRefFixture.programado(UUID.randomUUID());
        Compra compra = CompraFixture.confirmada(UUID.randomUUID(), viaje, UUID.randomUUID(), 12, 13);

        // Act
        byte[] pdf = new BoletosPdf(generadorQr).generar(compra, viaje, compra.getBoletos());

        // Assert
        assertEquals("%PDF", new String(pdf, 0, 4));
        PdfReader lector = new PdfReader(pdf);
        String texto = new PdfTextExtractor(lector).getTextFromPage(1);
        lector.close();
        assertTrue(texto.contains("Quetzaltenango"), texto);
        assertTrue(texto.contains("Carlos Méndez"), texto);
        assertTrue(texto.contains("Q 170.00"), texto);
        assertTrue(texto.contains("BOL-12") && texto.contains("BOL-13"), texto);
    }
}
