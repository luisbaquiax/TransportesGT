package org.transportsgt.ticketservice.services.consulta.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.transportsgt.ticketservice.dto.response.MapaAsientosResponse;
import org.transportsgt.ticketservice.dto.response.ViajeDisponibleResponse;
import org.transportsgt.ticketservice.exception.ViajeNoEncontradoException;
import org.transportsgt.ticketservice.fixture.ViajeRefFixture;
import org.transportsgt.ticketservice.mappers.ViajeMapperImpl;
import org.transportsgt.ticketservice.models.ViajeRef;
import org.transportsgt.ticketservice.repositories.BoletoRepository;
import org.transportsgt.ticketservice.repositories.ConteoAsientos;
import org.transportsgt.ticketservice.repositories.ViajeRefRepository;
import org.transportsgt.ticketservice.repositories.ViajeRefSpecs;
import org.transportsgt.ticketservice.utils.Fechas;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultaViajesServiceImplTest {

    @Mock
    private ViajeRefRepository viajeRefRepository;
    @Mock
    private BoletoRepository boletoRepository;
    @Mock
    private ViajeRefSpecs specs;

    private ConsultaViajesServiceImpl servicio;
    private ViajeRef viaje;
    private Specification<ViajeRef> filtro;

    @BeforeEach
    void setUp() {
        servicio = new ConsultaViajesServiceImpl(viajeRefRepository, boletoRepository, specs, new ViajeMapperImpl(),
                new Fechas(), Clock.fixed(ViajeRefFixture.AHORA, ZoneOffset.UTC));
        viaje = ViajeRefFixture.programado(UUID.randomUUID());
        filtro = (root, query, cb) -> null;
    }

    private static ConteoAsientos conteo(UUID idViaje, long ocupados) {
        return new ConteoAsientos() {
            @Override
            public UUID getIdViaje() {
                return idViaje;
            }

            @Override
            public long getOcupados() {
                return ocupados;
            }
        };
    }

    @Nested
    class ALaVenta {

        @Test
        void aLaVenta_cuandoHayFecha_filtraElDiaEnGuatemalaYCalculaLosDisponibles() {
            // Arrange
            LocalDate fecha = LocalDate.parse("2026-10-21");
            OffsetDateTime ahora = ViajeRefFixture.AHORA.atZone(Fechas.ZONA_GUATEMALA).toOffsetDateTime();
            OffsetDateTime desde = OffsetDateTime.parse("2026-10-21T00:00-06:00");
            OffsetDateTime hasta = OffsetDateTime.parse("2026-10-22T00:00-06:00");
            ViajeRef sinBus = ViajeRefFixture.programado(UUID.randomUUID());
            sinBus.setCapacidadBus(null);
            when(specs.aLaVenta(ahora, ViajeRefFixture.SUCURSAL_XELA, null, "guate", desde, hasta)).thenReturn(filtro);
            when(viajeRefRepository.findAll(eq(filtro), any(Sort.class))).thenReturn(List.of(viaje, sinBus));
            when(boletoRepository.contarOcupados(List.of(viaje.getId()))).thenReturn(List.of(conteo(viaje.getId(), 3)));

            // Act
            List<ViajeDisponibleResponse> viajes = servicio.aLaVenta(ViajeRefFixture.SUCURSAL_XELA, null, "guate", fecha);

            // Assert
            assertAll(
                    () -> assertEquals(1, viajes.size()),
                    () -> assertEquals(viaje.getId(), viajes.getFirst().idViaje()),
                    () -> assertEquals(37, viajes.getFirst().asientosDisponibles()),
                    () -> assertEquals((short) 40, viajes.getFirst().capacidad())
            );
        }

        @Test
        void aLaVenta_cuandoNoHayViajes_noCuentaAsientos() {
            // Arrange
            when(specs.aLaVenta(any(), eq(null), eq(null), eq(null), eq(null), eq(null))).thenReturn(filtro);
            when(viajeRefRepository.findAll(eq(filtro), any(Sort.class))).thenReturn(List.of());

            // Act & Assert
            assertTrue(servicio.aLaVenta(null, null, null, null).isEmpty());
            verifyNoInteractions(boletoRepository);
        }

        @Test
        void aLaVenta_cuandoUnViajeNoTieneBoletos_tieneTodosLosAsientosDisponibles() {
            // Arrange
            when(specs.aLaVenta(any(), any(), any(), any(), any(), any())).thenReturn(filtro);
            when(viajeRefRepository.findAll(eq(filtro), any(Sort.class))).thenReturn(List.of(viaje));
            when(boletoRepository.contarOcupados(List.of(viaje.getId()))).thenReturn(List.of());

            // Act & Assert
            assertEquals(40, servicio.aLaVenta(null, null, null, null).getFirst().asientosDisponibles());
        }
    }

    @Nested
    class MapaAsientos {

        @Test
        void mapaAsientos_devuelveCapacidadYOcupados() {
            // Arrange
            when(viajeRefRepository.findById(viaje.getId())).thenReturn(Optional.of(viaje));
            when(boletoRepository.findAsientosOcupados(viaje.getId())).thenReturn(List.of((short) 1, (short) 12));

            // Act
            MapaAsientosResponse mapa = servicio.mapaAsientos(viaje.getId());

            // Assert
            assertAll(
                    () -> assertEquals(40, mapa.capacidad()),
                    () -> assertEquals(List.of(1, 12), mapa.ocupados())
            );
        }

        @Test
        void mapaAsientos_cuandoNoTieneBus_reportaCapacidadCero() {
            // Arrange
            viaje.setCapacidadBus(null);
            when(viajeRefRepository.findById(viaje.getId())).thenReturn(Optional.of(viaje));
            when(boletoRepository.findAsientosOcupados(viaje.getId())).thenReturn(List.of());

            // Act & Assert
            assertEquals(0, servicio.mapaAsientos(viaje.getId()).capacidad());
        }

        @Test
        void mapaAsientos_cuandoNoExisteOFueEliminado_lanzaViajeNoEncontrado() {
            // Arrange
            UUID inexistente = UUID.randomUUID();
            viaje.setEliminado(true);
            when(viajeRefRepository.findById(inexistente)).thenReturn(Optional.empty());
            when(viajeRefRepository.findById(viaje.getId())).thenReturn(Optional.of(viaje));

            // Act & Assert
            assertAll(
                    () -> assertThrows(ViajeNoEncontradoException.class, () -> servicio.mapaAsientos(inexistente)),
                    () -> assertThrows(ViajeNoEncontradoException.class, () -> servicio.mapaAsientos(viaje.getId()))
            );
        }
    }

    @Nested
    class AsientoMaximo {

        @Test
        void asientoMaximo_devuelveElMayorAsientoActivo() {
            // Arrange
            when(boletoRepository.findAsientoMaximo(viaje.getId())).thenReturn((short) 33);

            // Act & Assert
            assertEquals(33, servicio.asientoMaximo(viaje.getId()).asientoMaximo());
            verify(boletoRepository).findAsientoMaximo(viaje.getId());
        }
    }
}
