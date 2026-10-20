package org.transportsgt.notificationservice.services.licencia.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.notificationservice.fixture.ChoferLicenciaFixture;
import org.transportsgt.notificationservice.fixture.ContactoFixture;
import org.transportsgt.notificationservice.fixture.PropiedadesFixture;
import org.transportsgt.notificationservice.models.ChoferLicencia;
import org.transportsgt.notificationservice.models.EstadoRecordatorio;
import org.transportsgt.notificationservice.models.RecordatorioLicencia;
import org.transportsgt.notificationservice.models.TipoNotificacion;
import org.transportsgt.notificationservice.repositories.RecordatorioLicenciaRepository;
import org.transportsgt.notificationservice.services.bandeja.BandejaSalida;
import org.transportsgt.notificationservice.services.bandeja.ClaveDedup;
import org.transportsgt.notificationservice.services.bandeja.Destinatario;
import org.transportsgt.notificationservice.services.bandeja.Destinatarios;
import org.transportsgt.notificationservice.services.bandeja.SolicitudCorreo;
import org.transportsgt.notificationservice.services.licencia.ResumenAvisos;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvisosLicenciaTest {

    @Nested
    class ProcesarUno {

        @Mock
        private RecordatorioLicenciaRepository recordatorioRepository;
        @Mock
        private Destinatarios destinatarios;
        @Mock
        private BandejaSalida bandeja;

        private GestorAvisoLicencia gestor;
        private ChoferLicencia chofer;
        private RecordatorioLicencia aviso;

        @BeforeEach
        void setUp() {
            gestor = new GestorAvisoLicencia(recordatorioRepository, destinatarios, bandeja, new ClaveDedup(),
                    PropiedadesFixture.fechas());
            chofer = ChoferLicenciaFixture.chofer(UUID.randomUUID());
            chofer.setFechaVencimientoLicencia(ChoferLicenciaFixture.HOY.plusDays(30));
            aviso = ChoferLicenciaFixture.aviso(chofer);
        }

        @Test
        void procesar_cuandoElAvisoEsVigente_encolaAlChoferYALosAdminsYMarcaEnviado() {
            // Arrange
            Destinatario admin1 = ContactoFixture.destinatario(UUID.randomUUID());
            Destinatario admin2 = ContactoFixture.destinatario(UUID.randomUUID());
            when(recordatorioRepository.findByIdParaActualizar(aviso.getId())).thenReturn(Optional.of(aviso));
            when(destinatarios.adminsDeSucursal(ContactoFixture.SUCURSAL_XELA)).thenReturn(List.of(admin1, admin2));
            ArgumentCaptor<SolicitudCorreo> solicitudes = ArgumentCaptor.forClass(SolicitudCorreo.class);

            // Act
            GestorAvisoLicencia.Resultado resultado = gestor.procesar(aviso.getId());

            // Assert
            verify(bandeja, times(3)).encolar(solicitudes.capture());
            SolicitudCorreo alChofer = solicitudes.getAllValues().getFirst();
            assertAll(
                    () -> assertEquals(GestorAvisoLicencia.Resultado.ENVIADO, resultado),
                    () -> assertEquals(EstadoRecordatorio.ENVIADO, aviso.getEstado()),
                    () -> assertEquals(OffsetDateTime.parse("2026-10-20T10:00-06:00"), aviso.getEnviadoEn()),
                    () -> assertEquals((short) 1, aviso.getIntentos()),
                    () -> assertEquals(TipoNotificacion.LICENCIA_POR_VENCER, alChofer.tipo()),
                    () -> assertEquals("juan.perez@transportes.gt", alChofer.destinatario().correo()),
                    () -> assertEquals("lic:" + aviso.getId() + ":juan.perez@transportes.gt", alChofer.claveDedup()),
                    () -> assertEquals(30L, alChofer.variables().get("dias")),
                    () -> assertEquals(false, alChofer.variables().get("paraAdmin")),
                    () -> assertEquals(true, solicitudes.getAllValues().get(1).variables().get("paraAdmin")),
                    () -> assertEquals(admin2, solicitudes.getAllValues().get(2).destinatario())
            );
        }

        @Test
        void procesar_cuandoElJobSeAtraso_usaLosDiasQueRealmenteFaltan() {
            // Arrange: el aviso de 30 días debía salir hace 5
            chofer.setFechaVencimientoLicencia(ChoferLicenciaFixture.HOY.plusDays(25));
            aviso.setFechaVencimiento(chofer.getFechaVencimientoLicencia());
            when(recordatorioRepository.findByIdParaActualizar(aviso.getId())).thenReturn(Optional.of(aviso));
            when(destinatarios.adminsDeSucursal(ContactoFixture.SUCURSAL_XELA)).thenReturn(List.of());
            ArgumentCaptor<SolicitudCorreo> solicitud = ArgumentCaptor.forClass(SolicitudCorreo.class);

            // Act
            gestor.procesar(aviso.getId());

            // Assert
            verify(bandeja).encolar(solicitud.capture());
            assertEquals(25L, solicitud.getValue().variables().get("dias"));
        }

        @Test
        void procesar_cuandoElChoferEstaInactivoOCambioLaFecha_cancelaSinCorreos() {
            // Arrange
            RecordatorioLicencia otraFecha = ChoferLicenciaFixture.aviso(chofer);
            otraFecha.setFechaVencimiento(LocalDate.parse("2026-12-01"));
            ChoferLicencia inactivo = ChoferLicenciaFixture.chofer(UUID.randomUUID());
            inactivo.setActivo(false);
            RecordatorioLicencia deInactivo = ChoferLicenciaFixture.aviso(inactivo);
            when(recordatorioRepository.findByIdParaActualizar(otraFecha.getId())).thenReturn(Optional.of(otraFecha));
            when(recordatorioRepository.findByIdParaActualizar(deInactivo.getId())).thenReturn(Optional.of(deInactivo));

            // Act & Assert
            assertAll(
                    () -> assertEquals(GestorAvisoLicencia.Resultado.CANCELADO, gestor.procesar(otraFecha.getId())),
                    () -> assertEquals(GestorAvisoLicencia.Resultado.CANCELADO, gestor.procesar(deInactivo.getId())),
                    () -> assertEquals(EstadoRecordatorio.CANCELADO, otraFecha.getEstado()),
                    () -> assertEquals(EstadoRecordatorio.CANCELADO, deInactivo.getEstado())
            );
            verifyNoInteractions(bandeja, destinatarios);
        }

        @Test
        void procesar_cuandoYaNoEstaPendienteONoExiste_loOmite() {
            // Arrange
            aviso.setEstado(EstadoRecordatorio.ENVIADO);
            UUID inexistente = UUID.randomUUID();
            when(recordatorioRepository.findByIdParaActualizar(aviso.getId())).thenReturn(Optional.of(aviso));
            when(recordatorioRepository.findByIdParaActualizar(inexistente)).thenReturn(Optional.empty());

            // Act & Assert
            assertAll(
                    () -> assertEquals(GestorAvisoLicencia.Resultado.OMITIDO, gestor.procesar(aviso.getId())),
                    () -> assertEquals(GestorAvisoLicencia.Resultado.OMITIDO, gestor.procesar(inexistente))
            );
            verifyNoInteractions(bandeja);
        }
    }

    @Nested
    class Procesar {

        @Mock
        private RecordatorioLicenciaRepository recordatorioRepository;

        private GestorAvisoLicencia gestor;
        private UUID enviado;
        private UUID cancelado;
        private UUID conError;

        @BeforeEach
        void setUp() {
            gestor = mock(GestorAvisoLicencia.class);
            enviado = UUID.randomUUID();
            cancelado = UUID.randomUUID();
            conError = UUID.randomUUID();
        }

        @Test
        void procesar_cuandoUnAvisoFalla_sigueConLosDemas() {
            // Arrange
            when(recordatorioRepository.findIdsPendientesHasta(ChoferLicenciaFixture.HOY))
                    .thenReturn(List.of(conError, enviado, cancelado));
            when(gestor.procesar(conError)).thenThrow(new IllegalStateException("bloqueo"));
            when(gestor.procesar(enviado)).thenReturn(GestorAvisoLicencia.Resultado.ENVIADO);
            when(gestor.procesar(cancelado)).thenReturn(GestorAvisoLicencia.Resultado.CANCELADO);

            // Act
            ResumenAvisos resumen = new AvisosLicenciaServiceImpl(recordatorioRepository, gestor,
                    PropiedadesFixture.fechas()).procesar();

            // Assert
            assertEquals(new ResumenAvisos(1, 1, 1), resumen);
        }
    }
}
