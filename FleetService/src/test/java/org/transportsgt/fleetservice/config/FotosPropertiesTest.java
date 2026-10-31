package org.transportsgt.fleetservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.transportsgt.fleetservice.almacenamiento.AlmacenamientoFotos;
import org.transportsgt.fleetservice.almacenamiento.AlmacenamientoFotosLocal;
import org.transportsgt.fleetservice.almacenamiento.AlmacenamientoFotosNube;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las claves de application.properties ({@code flota.fotos.nube.*}) deben llegar a {@link FotosProperties} y
 * elegir el almacenamiento correcto.
 */
class FotosPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(FotosProperties.class)
    @Import({AlmacenamientoFotosLocal.class, AlmacenamientoFotosNube.class})
    static class Configuracion {
    }

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(Configuracion.class)
            .withPropertyValues("flota.fotos.directorio=target/fotos-test", "flota.fotos.tamanio-maximo-bytes=1024");

    @Test
    void nubeHabilitada_recibeElBucketYUsaCloudStorage() {
        contexto.withPropertyValues("flota.fotos.nube.habilitada=true", "flota.fotos.nube.bucket=bucket_transportesgt")
                .run(arrancado -> {
                    assertThat(arrancado).hasNotFailed();
                    assertThat(arrancado.getBean(FotosProperties.class).nube().bucket()).isEqualTo("bucket_transportesgt");
                    assertThat(arrancado.getBean(AlmacenamientoFotos.class)).isInstanceOf(AlmacenamientoFotosNube.class);
                });
    }

    @Test
    void nubeHabilitadaSinBucket_noArranca() {
        contexto.withPropertyValues("flota.fotos.nube.habilitada=true", "flota.fotos.nube.bucket=")
                .run(arrancado -> assertThat(arrancado).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("FOTOS_NUBE_BUCKET"));
    }

    @Test
    void porDefecto_usaElDiscoLocal() {
        contexto.withPropertyValues("flota.fotos.nube.habilitada=false")
                .run(arrancado -> assertThat(arrancado.getBean(AlmacenamientoFotos.class))
                        .isInstanceOf(AlmacenamientoFotosLocal.class));
    }

    @Test
    void sinDirectorio_noArranca() {
        contexto.withPropertyValues("flota.fotos.directorio=", "flota.fotos.nube.habilitada=false")
                .run(arrancado -> assertThat(arrancado).hasFailed());
    }
}
