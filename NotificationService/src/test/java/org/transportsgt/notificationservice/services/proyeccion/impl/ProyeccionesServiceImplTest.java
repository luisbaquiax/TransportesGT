package org.transportsgt.notificationservice.services.proyeccion.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.payload.flota.DatosChofer;
import org.transportsgt.eventos.payload.identidad.DatosUsuario;
import org.transportsgt.eventos.payload.identidad.Rol;
import org.transportsgt.notificationservice.fixture.ChoferLicenciaFixture;
import org.transportsgt.notificationservice.fixture.ContactoFixture;
import org.transportsgt.notificationservice.fixture.EventoFixture;
import org.transportsgt.notificationservice.fixture.PropiedadesFixture;
import org.transportsgt.notificationservice.repositories.ChoferLicenciaRepository;
import org.transportsgt.notificationservice.repositories.ContactoRepository;
import org.transportsgt.notificationservice.repositories.RecordatorioLicenciaRepository;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProyeccionesServiceImplTest {

    @Nested
    class Contactos {

        @Mock
        private ContactoRepository contactoRepository;

        private ProyeccionContactosServiceImpl servicio;
        private DatosUsuario datos;

        @BeforeEach
        void setUp() {
            servicio = new ProyeccionContactosServiceImpl(contactoRepository);
            datos = EventoFixture.usuario(UUID.randomUUID(), Rol.ADMIN_SUCURSAL, ContactoFixture.SUCURSAL_XELA);
        }

        @Test
        void aplicar_cuandoLaVersionEsNueva_haceUpsertConElRol() {
            // Arrange
            when(contactoRepository.upsert(datos.idUsuario(), datos.correo(), datos.nombreCompleto(), "ADMIN_SUCURSAL",
                    ContactoFixture.SUCURSAL_XELA, true, 4L)).thenReturn(1);

            // Act & Assert
            assertTrue(servicio.aplicar(datos, 4L));
        }

        @Test
        void aplicar_cuandoLaVersionEsViejaOVieneSinVersion_devuelveFalse() {
            // Arrange
            when(contactoRepository.upsert(any(), any(), any(), any(), any(), anyBoolean(), eq(0L))).thenReturn(0);

            // Act & Assert
            assertFalse(servicio.aplicar(datos, null));
        }
    }

    @Nested
    class Choferes {

        @Mock
        private ChoferLicenciaRepository choferRepository;
        @Mock
        private RecordatorioLicenciaRepository recordatorioRepository;

        private ProyeccionChoferesServiceImpl servicio;
        private UUID idChofer;
        private LocalDate hoy;

        @BeforeEach
        void setUp() {
            servicio = new ProyeccionChoferesServiceImpl(choferRepository, recordatorioRepository,
                    new CalendarioRecordatorios(), PropiedadesFixture.fechas());
            idChofer = UUID.randomUUID();
            hoy = ChoferLicenciaFixture.HOY;
        }

        private void upsertAplica(DatosChofer datos, long version) {
            when(choferRepository.upsert(idChofer, datos.idSucursal(), datos.nombreCompleto(), datos.correo(),
                    datos.fechaVencimientoLicencia(), datos.activo(), version)).thenReturn(1);
        }

        @Test
        void aplicar_cuandoEsActivo_cancelaOtrasFechasYProgramaLosTresAvisos() {
            // Arrange
            DatosChofer datos = EventoFixture.chofer(idChofer, ChoferLicenciaFixture.VENCIMIENTO, true);
            upsertAplica(datos, 2L);

            // Act
            boolean aplicado = servicio.aplicar(datos, 2L);

            // Assert
            assertTrue(aplicado);
            InOrder orden = inOrder(recordatorioRepository);
            orden.verify(recordatorioRepository).cancelarPendientesDeOtraFecha(idChofer, ChoferLicenciaFixture.VENCIMIENTO);
            orden.verify(recordatorioRepository).reactivarCancelados(idChofer, ChoferLicenciaFixture.VENCIMIENTO, hoy);
            orden.verify(recordatorioRepository).programar(idChofer, ChoferLicenciaFixture.VENCIMIENTO, (short) 30,
                    LocalDate.parse("2027-02-13"));
            orden.verify(recordatorioRepository).programar(idChofer, ChoferLicenciaFixture.VENCIMIENTO, (short) 15,
                    LocalDate.parse("2027-02-28"));
            orden.verify(recordatorioRepository).programar(idChofer, ChoferLicenciaFixture.VENCIMIENTO, (short) 3,
                    LocalDate.parse("2027-03-12"));
            verify(recordatorioRepository, never()).cancelarPendientes(any());
        }

        @Test
        void aplicar_cuandoAlgunAvisoYaPaso_noLoProgramaPeroSiLosDemas() {
            // Arrange: vence en 10 días → solo queda el de 3
            LocalDate vencimiento = hoy.plusDays(10);
            DatosChofer datos = EventoFixture.chofer(idChofer, vencimiento, true);
            upsertAplica(datos, 1L);

            // Act
            servicio.aplicar(datos, 1L);

            // Assert
            verify(recordatorioRepository).programar(idChofer, vencimiento, (short) 3, hoy.plusDays(7));
            verify(recordatorioRepository, never()).programar(any(), any(), eq((short) 30), any());
            verify(recordatorioRepository, never()).programar(any(), any(), eq((short) 15), any());
        }

        @Test
        void aplicar_cuandoElChoferSeDesactiva_cancelaTodosSinProgramar() {
            // Arrange
            DatosChofer datos = EventoFixture.chofer(idChofer, ChoferLicenciaFixture.VENCIMIENTO, false);
            upsertAplica(datos, 3L);

            // Act
            boolean aplicado = servicio.aplicar(datos, 3L);

            // Assert
            assertTrue(aplicado);
            verify(recordatorioRepository).cancelarPendientes(idChofer);
            verify(recordatorioRepository, never()).programar(any(), any(), anyShort(), any());
        }

        @Test
        void aplicar_cuandoLaVersionEsVieja_noTocaLosAvisos() {
            // Arrange
            DatosChofer datos = EventoFixture.chofer(idChofer, ChoferLicenciaFixture.VENCIMIENTO, true);
            when(choferRepository.upsert(any(), any(), any(), any(), any(), anyBoolean(), anyLong())).thenReturn(0);

            // Act & Assert
            assertFalse(servicio.aplicar(datos, null));
            verifyNoInteractions(recordatorioRepository);
        }
    }
}
