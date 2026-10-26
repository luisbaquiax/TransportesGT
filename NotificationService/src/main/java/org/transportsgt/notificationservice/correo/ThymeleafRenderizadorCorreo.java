package org.transportsgt.notificationservice.correo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;
import org.transportsgt.notificationservice.models.TipoNotificacion;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plantillas {@code templates/correo/<tipo>.html} (p. ej. {@code compra-boletos.html}), todas sobre
 * {@code correo/layout.html}. El asunto es el {@code <title>} de la plantilla, así vive junto al cuerpo.
 */
@Component
@RequiredArgsConstructor
public class ThymeleafRenderizadorCorreo implements RenderizadorCorreo {

    private static final Locale ESPANOL = Locale.forLanguageTag("es-GT");
    private static final Pattern TITULO = Pattern.compile("(?is)<title>(.*?)</title>");

    private final ITemplateEngine motor;
    private final FormatoCorreo formato;

    @Override
    public CorreoRenderizado renderizar(TipoNotificacion tipo, Map<String, Object> variables) {
        Context contexto = new Context(ESPANOL, variables);
        contexto.setVariable("fmt", formato);
        String html = motor.process(plantilla(tipo), contexto);
        return new CorreoRenderizado(asunto(html, tipo), html);
    }

    static String plantilla(TipoNotificacion tipo) {
        return "correo/" + tipo.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String asunto(String html, TipoNotificacion tipo) {
        Matcher titulo = TITULO.matcher(html);
        if (!titulo.find() || titulo.group(1).isBlank()) {
            throw new IllegalStateException("La plantilla " + plantilla(tipo) + " no tiene <title>");
        }
        return titulo.group(1).strip().replaceAll("\\s+", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&");
    }
}
