package org.transportsgt.ticketservice.config;

import org.transportsgt.ticketservice.config.web.FiltroCorrelacion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.transportsgt.ticketservice.clients.CarteraClient;
import org.transportsgt.eventos.contexto.ContextoCorrelacion;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

/**
 * {@link CarteraClient} sobre {@code RestClient}: URL, tiempo límite y reenvío de la correlación.
 */
@Configuration
public class CarteraClientConfig {

    @Bean
    public CarteraClient carteraClient(@Value("${cartera.url}") String url,
                                       @Value("${cartera.timeout}") Duration timeout) {
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        fabrica.setReadTimeout(timeout);
        RestClient restClient = RestClient.builder()
                .baseUrl(url)
                .requestFactory(fabrica)
                .requestInterceptor((peticion, cuerpo, ejecucion) -> {
                    UUID idCorrelacion = ContextoCorrelacion.actual();
                    if (idCorrelacion != null) {
                        peticion.getHeaders().set(FiltroCorrelacion.CABECERA, idCorrelacion.toString());
                    }
                    return ejecucion.execute(peticion, cuerpo);
                })
                .build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient)).build()
                .createClient(CarteraClient.class);
    }
}
