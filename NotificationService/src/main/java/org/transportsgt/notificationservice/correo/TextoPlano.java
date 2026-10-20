package org.transportsgt.notificationservice.correo;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Texto alternativo de un correo HTML, para los clientes que no muestran HTML. Las plantillas son simples (tablas,
 * párrafos y enlaces), así que basta con quitar etiquetas y respetar los saltos de línea.
 */
@Component
public class TextoPlano {

    private static final Pattern SIN_CONTENIDO = Pattern.compile("(?is)<(head|style|script)[^>]*>.*?</\\1>");
    private static final Pattern ENLACE = Pattern.compile("(?is)<a\\s[^>]*href=\"([^\"]*)\"[^>]*>(.*?)</a>");
    private static final Pattern SALTO = Pattern.compile("(?i)<br\\s*/?>|</(p|div|tr|h[1-6]|li|table)>");
    private static final Pattern CELDA = Pattern.compile("(?i)</t[dh]>");
    private static final Pattern ETIQUETA = Pattern.compile("<[^>]+>");

    public String desdeHtml(String html) {
        String texto = SIN_CONTENIDO.matcher(html).replaceAll("");
        // El enlace se conserva visible: en texto plano no hay botones
        texto = ENLACE.matcher(texto).replaceAll("$2: $1");
        texto = SALTO.matcher(texto).replaceAll("\n");
        texto = CELDA.matcher(texto).replaceAll(" ");
        texto = ETIQUETA.matcher(texto).replaceAll("");
        texto = texto.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&");
        return texto.lines()
                .map(String::strip)
                .collect(Collectors.joining("\n"))
                .replaceAll("\n{3,}", "\n\n")
                .strip();
    }
}
