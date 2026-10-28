package org.transportsgt.reportingservice.services.proyecciones.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.mensajeria.excepciones.EventoNoRecuperableException;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.flota.DatosBus;
import org.transportsgt.eventos.payload.flota.DatosChofer;
import org.transportsgt.eventos.payload.identidad.DatosSucursal;
import org.transportsgt.eventos.payload.flota.EstadoOperativo;
import org.transportsgt.eventos.payload.flota.TipoLicencia;
import org.transportsgt.reportingservice.fixture.EventoFixture;
import org.transportsgt.reportingservice.fixture.RepSucursalFixture;
import org.transportsgt.reportingservice.repositories.RepBusRepository;
import org.transportsgt.reportingservice.repositories.RepChoferRepository;
import org.transportsgt.reportingservice.repositories.RepSucursalRepository;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProyeccionSucursalYFlotaServiceImplTest {

    @Mock
    private RepSucursalRepository sucursalRepository;
    @Mock
    private RepBusRepository busRepository;
    @Mock
    private RepChoferRepository choferRepository;

    private ProyeccionSucursalServiceImpl sucursalService;
    private ProyeccionFlotaServiceImpl flotaService;
    private UUID idSucursal;

    @BeforeEach
    void setUp() {
        sucursalService = new ProyeccionSucursalServiceImpl(new CamposObligatorios(), sucursalRepository,
                new VersionAgregado());
        flotaService = new ProyeccionFlotaServiceImpl(new CamposObligatorios(), busRepository, choferRepository,
                new VersionAgregado());
        idSucursal = RepSucursalFixture.QUETZALTENANGO;
    }

    @Nested
    class AplicarSucursal {

        @Test
        void aplicarSucursal_cuandoTraeVersion_haceUpsertConEsaVersion() {
            // Arrange
            DatosSucursal datos = EventoFixture.sucursal(idSucursal, "Quetzaltenango");
            when(sucursalRepository.upsert(any(), any(), any(), any(), any(), anyBoolean(), anyLong())).thenReturn(1);

            // Act
            sucursalService.aplicarSucursal(EventoFixture.sobre(TipoEvento.SUCURSAL_ACTUALIZADA, datos, 3L));

            // Assert
            verify(sucursalRepository).upsert(idSucursal, "Quetzaltenango", "Quetzaltenango", datos.latitud(),
                    datos.longitud(), true, 3L);
        }

        @Test
        void aplicarSucursal_cuandoVersionViejaYSinVersion_usaCeroYNoFalla() {
            // Arrange
            when(sucursalRepository.upsert(any(), any(), any(), any(), any(), anyBoolean(), anyLong())).thenReturn(0);

            // Act
            sucursalService.aplicarSucursal(EventoFixture.sobre(TipoEvento.SUCURSAL_CREADA,
                    EventoFixture.sucursal(idSucursal, "Xela")));

            // Assert
            verify(sucursalRepository).upsert(eq(idSucursal), eq("Xela"), any(), any(), any(), eq(true), eq(0L));
        }

        @Test
        void aplicarSucursal_cuandoFaltanDatos_esNoRecuperable() {
            // Arrange
            SobreEvento<DatosSucursal> sinDatos = EventoFixture.sobre(TipoEvento.SUCURSAL_CREADA, null, 1L);
            SobreEvento<DatosSucursal> sinId = EventoFixture.sobre(TipoEvento.SUCURSAL_CREADA,
                    EventoFixture.sucursal(null, "Xela"), 1L);

            // Act & Assert
            assertThrows(EventoNoRecuperableException.class, () -> sucursalService.aplicarSucursal(sinDatos));
            assertThrows(EventoNoRecuperableException.class, () -> sucursalService.aplicarSucursal(sinId));
            verifyNoInteractions(sucursalRepository);
        }
    }

    @Nested
    class AplicarBus {

        @Test
        void aplicarBus_cuandoLlega_haceUpsertConEstadoYVersion() {
            // Arrange
            UUID idBus = UUID.randomUUID();
            when(busRepository.upsert(any(), any(), any(), any(), any(), anyShort(), any(), anyBoolean(), anyLong()))
                    .thenReturn(1);

            // Act
            flotaService.aplicarBus(EventoFixture.sobre(TipoEvento.BUS_REGISTRADO, EventoFixture.bus(idBus, idSucursal), 2L));

            // Assert
            verify(busRepository).upsert(idBus, idSucursal, "P900ABC", "Toyota", "Hiace", (short) 45, "DISPONIBLE",
                    true, 2L);
        }

        @Test
        void aplicarBus_cuandoVersionViejaYSinEstado_pasaEstadoNulo() {
            // Arrange
            UUID idBus = UUID.randomUUID();
            DatosBus sinEstado = new DatosBus(idBus, idSucursal, "P1", null, null, 2020, 30, null, false);
            when(busRepository.upsert(any(), any(), any(), any(), any(), anyShort(), any(), anyBoolean(), anyLong()))
                    .thenReturn(0);

            // Act
            flotaService.aplicarBus(EventoFixture.sobre(TipoEvento.BUS_ACTUALIZADO, sinEstado, 1L));

            // Assert
            verify(busRepository).upsert(idBus, idSucursal, "P1", null, null, (short) 30, null, false, 1L);
        }

        @Test
        void aplicarBus_cuandoNoTraeIdBus_esNoRecuperable() {
            // Arrange
            SobreEvento<DatosBus> evento = EventoFixture.sobre(TipoEvento.BUS_REGISTRADO,
                    new DatosBus(null, idSucursal, "P1", null, null, 2020, 30, EstadoOperativo.DISPONIBLE, true), 1L);

            // Act & Assert
            assertThrows(EventoNoRecuperableException.class, () -> flotaService.aplicarBus(evento));
        }
    }

    @Nested
    class AplicarChofer {

        @Test
        void aplicarChofer_cuandoLlega_haceUpsertConLicencia() {
            // Arrange
            UUID idChofer = UUID.randomUUID();
            DatosChofer datos = EventoFixture.chofer(idChofer, idSucursal);
            when(choferRepository.upsert(any(), any(), any(), any(), any(), any(), anyBoolean(), anyLong()))
                    .thenReturn(1);

            // Act
            flotaService.aplicarChofer(EventoFixture.sobre(TipoEvento.CHOFER_REGISTRADO, datos, 4L));

            // Assert
            verify(choferRepository).upsert(idChofer, idSucursal, "LIC-1", "José Méndez", TipoLicencia.A.name(),
                    datos.fechaVencimientoLicencia(), true, 4L);
        }

        @Test
        void aplicarChofer_cuandoVersionViejaYSinTipo_pasaTipoNulo() {
            // Arrange
            UUID idChofer = UUID.randomUUID();
            DatosChofer datos = new DatosChofer(idChofer, null, idSucursal, "Ana", null, null, null, null, null, null,
                    false);
            when(choferRepository.upsert(any(), any(), any(), any(), any(), any(), anyBoolean(), anyLong()))
                    .thenReturn(0);

            // Act
            flotaService.aplicarChofer(EventoFixture.sobre(TipoEvento.CHOFER_ACTUALIZADO, datos, 1L));

            // Assert
            verify(choferRepository).upsert(idChofer, idSucursal, null, "Ana", null, null, false, 1L);
        }
    }
}
