package org.transportsgt.ticketservice.documentos;

import lombok.RequiredArgsConstructor;
import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.Image;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.payload.boletos.Canal;
import org.transportsgt.ticketservice.models.Boleto;
import org.transportsgt.ticketservice.models.Compra;
import org.transportsgt.ticketservice.models.ViajeRef;
import org.transportsgt.ticketservice.utils.Fechas;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * PDF de una compra confirmada: datos del viaje y del comprador y, por cada boleto, su asiento, su código y el QR
 * que se escanea al abordar.
 */
@Component
@RequiredArgsConstructor
public class BoletosPdf {

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Color PRIMARIO = new Color(0x1F, 0x5F, 0x7A);
    private static final Color GRIS = new Color(0x5B, 0x67, 0x70);
    private static final Color LINEA = new Color(0xD8, 0xDE, 0xE3);
    private static final int LADO_QR_PX = 360;
    private static final float LADO_QR_PT = 120f;

    private static final Font TITULO = new Font(Font.HELVETICA, 18, Font.BOLD, PRIMARIO);
    private static final Font SUBTITULO = new Font(Font.HELVETICA, 11, Font.BOLD, PRIMARIO);
    private static final Font ETIQUETA = new Font(Font.HELVETICA, 9, Font.NORMAL, GRIS);
    private static final Font VALOR = new Font(Font.HELVETICA, 10, Font.BOLD, Color.BLACK);
    private static final Font CODIGO = new Font(Font.COURIER, 16, Font.BOLD, Color.BLACK);
    private static final Font NOTA = new Font(Font.HELVETICA, 8, Font.ITALIC, GRIS);

    private final GeneradorQr generadorQr;

    /**
     * @param boletos los boletos {@code CONFIRMADO} de la compra, en orden de asiento
     */
    public byte[] generar(Compra compra, ViajeRef viaje, List<Boleto> boletos) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.LETTER, 40, 40, 40, 40);
        try {
            PdfWriter.getInstance(documento, salida);
            documento.addTitle("Boletos " + compra.getId());
            documento.addCreator("TransportesGT");
            documento.open();
            documento.add(new Paragraph("TransportesGT", TITULO));
            documento.add(new Paragraph("Boletos de viaje · " + boletos.size() + (boletos.size() == 1 ? " pasajero" : " pasajeros"), ETIQUETA));
            documento.add(Chunk.NEWLINE);
            documento.add(seccion("Viaje"));
            documento.add(datos(
                    "Ruta", viaje.getNombreOrigen() + " – " + viaje.getNombreDestino(),
                    "Bus", viaje.getPlacaBus() == null ? "Por asignar" : viaje.getPlacaBus(),
                    "Salida", fechaHora(viaje.getFechaHoraSalida()),
                    "Llegada estimada", fechaHora(viaje.getFechaHoraLlegadaEstimada())));
            documento.add(seccion("Compra"));
            documento.add(datos(
                    "Comprador", compra.getNombreComprador(),
                    "NIT", compra.getNitComprador(),
                    "Canal", compra.getCanal() == Canal.EN_LINEA ? "En línea" : "Mostrador",
                    "Fecha de pago", compra.getFechaPago() == null ? "—" : compra.getFechaPago().format(FECHA),
                    "Precio por boleto", quetzales(compra.getPrecioUnitario()),
                    "Total", quetzales(compra.getMontoTotal())));
            documento.add(new Paragraph("Compra " + compra.getId(), NOTA));
            documento.add(seccion("Boletos"));
            for (Boleto boleto : boletos) {
                documento.add(boleto(boleto));
            }
            documento.add(new Paragraph("Presente el código QR de cada pasajero al abordar. El boleto es válido solo "
                    + "para el viaje y el asiento indicados.", NOTA));
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF de la compra " + compra.getId(), e);
        } finally {
            documento.close();
        }
        return salida.toByteArray();
    }

    private Paragraph seccion(String titulo) {
        Paragraph parrafo = new Paragraph(titulo, SUBTITULO);
        parrafo.setSpacingBefore(10);
        parrafo.setSpacingAfter(4);
        return parrafo;
    }

    /** Tabla de dos pares etiqueta-valor por fila. */
    private PdfPTable datos(String... pares) {
        PdfPTable tabla = new PdfPTable(4);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[]{1.2f, 2f, 1.2f, 2f});
        for (int i = 0; i < pares.length; i += 2) {
            tabla.addCell(celda(new Phrase(pares[i], ETIQUETA)));
            tabla.addCell(celda(new Phrase(pares[i + 1], VALOR)));
        }
        tabla.completeRow();
        return tabla;
    }

    private PdfPTable boleto(Boleto boleto) {
        PdfPTable tabla = new PdfPTable(2);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[]{1f, 3f});
        tabla.setSpacingBefore(6);
        tabla.setKeepTogether(true);

        Image qr = qr(boleto.getCodigo());
        PdfPCell celdaQr = new PdfPCell(qr, false);
        celdaQr.setPadding(8);
        celdaQr.setBorderColor(LINEA);
        celdaQr.setHorizontalAlignment(Element.ALIGN_CENTER);
        tabla.addCell(celdaQr);

        PdfPCell detalle = new PdfPCell();
        detalle.setPadding(12);
        detalle.setBorderColor(LINEA);
        detalle.setVerticalAlignment(Element.ALIGN_MIDDLE);
        detalle.addElement(new Paragraph("Asiento", ETIQUETA));
        detalle.addElement(new Paragraph(String.valueOf(boleto.getNumeroAsiento()), TITULO));
        detalle.addElement(new Paragraph("Código del boleto", ETIQUETA));
        detalle.addElement(new Paragraph(boleto.getCodigo(), CODIGO));
        tabla.addCell(detalle);
        return tabla;
    }

    private Image qr(String codigo) {
        try {
            Image imagen = Image.getInstance(generadorQr.png(codigo, LADO_QR_PX));
            imagen.scaleAbsolute(LADO_QR_PT, LADO_QR_PT);
            return imagen;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static PdfPCell celda(Phrase contenido) {
        PdfPCell celda = new PdfPCell(contenido);
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setPaddingBottom(5);
        return celda;
    }

    private static String fechaHora(OffsetDateTime instante) {
        return instante.atZoneSameInstant(Fechas.ZONA_GUATEMALA).format(FECHA_HORA);
    }

    private static String quetzales(BigDecimal monto) {
        return "Q " + monto.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
