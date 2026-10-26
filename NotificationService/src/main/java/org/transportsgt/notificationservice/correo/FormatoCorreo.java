package org.transportsgt.notificationservice.correo;

import org.springframework.stereotype.Component;
import org.transportsgt.notificationservice.utils.Fechas;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Formatos de los correos. Las plantillas lo reciben como {@code fmt}: {@code ${fmt.dinero(datos.montoTotal())}}.
 */
@Component
public class FormatoCorreo {

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String SIN_DATO = "—";

    /**
     * {@code 3800.5} → {@code Q 3,800.50}.
     */
    public String dinero(BigDecimal monto) {
        if (monto == null) {
            return SIN_DATO;
        }
        DecimalFormat formato = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return "Q " + formato.format(monto.setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * Fecha y hora en Guatemala: acepta {@link Instant}, {@link OffsetDateTime} o {@link ZonedDateTime}.
     */
    public String fechaHora(Object instante) {
        ZonedDateTime enGuatemala = switch (instante) {
            case Instant i -> i.atZone(Fechas.ZONA_GUATEMALA);
            case OffsetDateTime o -> o.atZoneSameInstant(Fechas.ZONA_GUATEMALA);
            case ZonedDateTime z -> z.withZoneSameInstant(Fechas.ZONA_GUATEMALA);
            case null, default -> null;
        };
        return enGuatemala == null ? SIN_DATO : FECHA_HORA.format(enGuatemala);
    }

    public String fecha(LocalDate fecha) {
        return fecha == null ? SIN_DATO : FECHA.format(fecha);
    }
}
