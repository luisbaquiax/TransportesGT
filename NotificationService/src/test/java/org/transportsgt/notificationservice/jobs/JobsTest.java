package org.transportsgt.notificationservice.jobs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.notificationservice.services.envio.EnvioCorreosService;
import org.transportsgt.notificationservice.services.envio.ResumenEnvio;
import org.transportsgt.notificationservice.services.licencia.AvisosLicenciaService;
import org.transportsgt.notificationservice.services.licencia.ResumenAvisos;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobsTest {

    @Mock
    private EnvioCorreosService envio;
    @Mock
    private AvisosLicenciaService avisos;

    @Test
    void envio_cuandoHayOnoHayCorreos_procesaUnLote() {
        // Arrange
        EnvioCorreosJob job = new EnvioCorreosJob(envio);
        when(envio.procesar()).thenReturn(
                new ResumenEnvio(3, 1, 0),
                new ResumenEnvio(0, 0, 0));

        // Act
        job.ejecutar();
        job.ejecutar();

        // Assert
        verify(envio, times(2)).procesar();
    }

    @Test
    void licencias_cuandoCorre_procesaLosAvisosDelDia() {
        // Arrange
        when(avisos.procesar()).thenReturn(new ResumenAvisos(2, 1, 0));

        // Act
        new AvisosLicenciaJob(avisos).ejecutar();

        // Assert
        verify(avisos).procesar();
    }
}
