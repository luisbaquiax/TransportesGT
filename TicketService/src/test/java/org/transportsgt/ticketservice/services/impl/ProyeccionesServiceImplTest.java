package org.transportsgt.ticketservice.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.ticketservice.fixture.EventoFixture;
import org.transportsgt.ticketservice.fixture.ViajeRefFixture;
import org.transportsgt.ticketservice.models.ViajeRef;
import org.transportsgt.ticketservice.repositories.ClienteRefRepository;
import org.transportsgt.ticketservice.repositories.ViajeRefRepository;
import org.transportsgt.eventos.payload.EstadoViaje;
import org.transportsgt.eventos.payload.TipoViaje;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProyeccionesServiceImplTest {

    @Nested
    class Viajes {

        @Mock
        private ViajeRefRepository viajeRefRepository;

        private ProyeccionViajesServiceImpl servicio;
        private ViajeRef viaje;

        @BeforeEach
        void setUp() {
            servicio = new ProyeccionViajesServiceImpl(viajeRefRepository);
            viaje = ViajeRefFixture.programado(UUID.randomUUID());
        }

        @Test
        void aplicar_cuandoEsRegular_haceUpsertConLaVersionDelSobre() {
            // Arrange
            when(viajeRefRepository.upsert(eq(viaje.getId()), eq(viaje.getIdRuta()), any(), any(), any(), any(), any(),
                    any(), eq(viaje.getIdBus()), eq("P-123ABC"), eq((short) 40), eq("PROGRAMADO"), eq(7L))).thenReturn(1);

            // Act & Assert
            assertTrue(servicio.aplicar(EventoFixture.viaje(viaje, TipoViaje.REGULAR, EstadoViaje.PROGRAMADO), 7L));
        }

        @Test
        void aplicar_cuandoNoTieneBusNiVersion_mandaNulosYVersionCero() {
            // Arrange
            viaje.setIdBus(null);
            when(viajeRefRepository.upsert(any(), any(), any(), any(), any(), any(), any(), any(), isNull(), isNull(),
                    isNull(), anyString(), eq(0L))).thenReturn(1);

            // Act & Assert
            assertTrue(servicio.aplicar(EventoFixture.viaje(viaje, TipoViaje.REGULAR, EstadoViaje.PROGRAMADO), null));
        }

        @Test
        void aplicar_cuandoLaVersionEsVieja_devuelveFalse() {
            // Arrange
            when(viajeRefRepository.upsert(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                    anyShort(), anyString(), anyLong())).thenReturn(0);

            // Act & Assert
            assertFalse(servicio.aplicar(EventoFixture.viaje(viaje, TipoViaje.REGULAR, EstadoViaje.EN_TRANSITO), 1L));
        }

        @Test
        void aplicar_cuandoEsAlquilerPrivado_loIgnora() {
            // Act & Assert
            assertFalse(servicio.aplicar(EventoFixture.viaje(viaje, TipoViaje.ALQUILER_PRIVADO, EstadoViaje.PROGRAMADO), 1L));
            verifyNoInteractions(viajeRefRepository);
        }
    }

    @Nested
    class Clientes {

        @Mock
        private ClienteRefRepository clienteRefRepository;

        private ProyeccionClientesServiceImpl servicio;

        @BeforeEach
        void setUp() {
            servicio = new ProyeccionClientesServiceImpl(clienteRefRepository);
        }

        @Test
        void aplicar_haceUpsertDelPerfil() {
            // Arrange
            UUID idUsuario = UUID.randomUUID();
            when(clienteRefRepository.upsert(idUsuario, "1234567K", "Carlos Méndez", true, 3L)).thenReturn(1);

            // Act & Assert
            assertTrue(servicio.aplicar(EventoFixture.perfil(idUsuario, "1234567K"), 3L));
        }

        @Test
        void aplicar_sinVersion_usaCeroYReportaSiNoCambio() {
            // Arrange
            UUID idUsuario = UUID.randomUUID();
            when(clienteRefRepository.upsert(idUsuario, null, "Carlos Méndez", true, 0L)).thenReturn(0);

            // Act & Assert
            assertFalse(servicio.aplicar(EventoFixture.perfil(idUsuario, null), null));
            verify(clienteRefRepository).upsert(idUsuario, null, "Carlos Méndez", true, 0L);
        }
    }
}
