package org.transportsgt.reportingservice.services.comun;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.reportingservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.reportingservice.exception.BusNoEncontradoException;
import org.transportsgt.reportingservice.exception.RutaNoEncontradaException;
import org.transportsgt.reportingservice.exception.SinPermisoSucursalException;
import org.transportsgt.reportingservice.exception.SucursalNoEncontradaException;
import org.transportsgt.reportingservice.exception.SucursalRequeridaException;
import org.transportsgt.reportingservice.fixture.RepBusFixture;
import org.transportsgt.reportingservice.fixture.RepRutaFixture;
import org.transportsgt.reportingservice.fixture.RepSucursalFixture;
import org.transportsgt.reportingservice.models.RepSucursal;
import org.transportsgt.reportingservice.repositories.RepBusRepository;
import org.transportsgt.reportingservice.repositories.RepRutaRepository;
import org.transportsgt.reportingservice.repositories.RepSucursalRepository;
import org.transportsgt.reportingservice.support.Soporte;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlcanceSucursalTest {

    @Mock
    private RepSucursalRepository sucursalRepository;
    @Mock
    private RepBusRepository busRepository;
    @Mock
    private RepRutaRepository rutaRepository;

    private AlcanceSucursal alcance;
    private RepSucursal xela;
    private UsuarioAutenticado adminXela;
    private UsuarioAutenticado sistema;

    @BeforeEach
    void setUp() {
        alcance = new AlcanceSucursal(sucursalRepository, busRepository, rutaRepository);
        xela = RepSucursalFixture.quetzaltenango();
        adminXela = Soporte.adminSucursal(RepSucursalFixture.QUETZALTENANGO);
        sistema = Soporte.adminSistema();
    }

    @Nested
    class SucursalDelReporte {

        @Test
        void sucursalDelReporte_adminSucursalSinPedir_usaLaDelToken() {
            // Arrange
            when(sucursalRepository.findById(RepSucursalFixture.QUETZALTENANGO)).thenReturn(Optional.of(xela));

            // Act & Assert
            assertSame(xela, alcance.sucursalDelReporte(adminXela, null));
        }

        @Test
        void sucursalDelReporte_adminSucursalPidiendoLaSuya_laDevuelve() {
            // Arrange
            when(sucursalRepository.findById(RepSucursalFixture.QUETZALTENANGO)).thenReturn(Optional.of(xela));

            // Act & Assert
            assertSame(xela, alcance.sucursalDelReporte(adminXela, RepSucursalFixture.QUETZALTENANGO));
        }

        @Test
        void sucursalDelReporte_adminSucursalPidiendoOtra_lanza403() {
            assertThrows(SinPermisoSucursalException.class,
                    () -> alcance.sucursalDelReporte(adminXela, RepSucursalFixture.GUATEMALA));
            verifyNoInteractions(sucursalRepository);
        }

        @Test
        void sucursalDelReporte_adminSistemaSinSucursal_lanza400() {
            assertThrows(SucursalRequeridaException.class, () -> alcance.sucursalDelReporte(sistema, null));
        }

        @Test
        void sucursalDelReporte_adminSistemaConSucursalInexistente_lanza404() {
            // Arrange
            when(sucursalRepository.findById(RepSucursalFixture.GUATEMALA)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(SucursalNoEncontradaException.class,
                    () -> alcance.sucursalDelReporte(sistema, RepSucursalFixture.GUATEMALA));
        }

        @Test
        void sucursalDelReporte_adminSucursalSinSucursalEnElToken_lanza400() {
            assertThrows(SucursalRequeridaException.class,
                    () -> alcance.sucursalDelReporte(Soporte.adminSucursal(null), null));
        }
    }

    @Nested
    class Catalogo {

        @Test
        void sucursalDelCatalogo_sistemaPuedeOmitirYSucursalSiempreLaSuya() {
            assertAll(
                    () -> assertNull(alcance.sucursalDelCatalogo(sistema, null)),
                    () -> assertEquals(RepSucursalFixture.GUATEMALA,
                            alcance.sucursalDelCatalogo(sistema, RepSucursalFixture.GUATEMALA)),
                    () -> assertEquals(RepSucursalFixture.QUETZALTENANGO, alcance.sucursalDelCatalogo(adminXela, null)),
                    () -> assertThrows(SinPermisoSucursalException.class,
                            () -> alcance.sucursalDelCatalogo(adminXela, RepSucursalFixture.GUATEMALA))
            );
        }

        @Test
        void sucursalOpcional_cuandoNoSePide_noConsulta() {
            assertTrue(alcance.sucursalOpcional(null).isEmpty());
            verifyNoInteractions(sucursalRepository);
        }

        @Test
        void sucursalOpcional_cuandoSePide_laBusca() {
            // Arrange
            when(sucursalRepository.findById(RepSucursalFixture.QUETZALTENANGO)).thenReturn(Optional.of(xela));

            // Act & Assert
            assertEquals(Optional.of(xela), alcance.sucursalOpcional(RepSucursalFixture.QUETZALTENANGO));
        }
    }

    @Nested
    class BusYRuta {

        @Test
        void busDeSucursal_casos() {
            // Arrange
            UUID idBus = UUID.randomUUID();
            UUID inexistente = UUID.randomUUID();
            when(busRepository.findById(idBus)).thenReturn(Optional.of(
                    RepBusFixture.bus(idBus, RepSucursalFixture.QUETZALTENANGO, "P900ABC")));
            when(busRepository.findById(inexistente)).thenReturn(Optional.empty());

            // Act & Assert
            assertAll(
                    () -> assertTrue(alcance.busDeSucursal(null, RepSucursalFixture.QUETZALTENANGO).isEmpty()),
                    () -> assertEquals("P900ABC",
                            alcance.busDeSucursal(idBus, RepSucursalFixture.QUETZALTENANGO).orElseThrow().getPlaca()),
                    () -> assertTrue(alcance.busDeSucursal(idBus, null).isPresent()),
                    () -> assertThrows(SinPermisoSucursalException.class,
                            () -> alcance.busDeSucursal(idBus, RepSucursalFixture.GUATEMALA)),
                    () -> assertThrows(BusNoEncontradoException.class,
                            () -> alcance.busDeSucursal(inexistente, null))
            );
        }

        @Test
        void rutaDeSucursal_casos() {
            // Arrange
            UUID idRuta = UUID.randomUUID();
            UUID inexistente = UUID.randomUUID();
            when(rutaRepository.findById(idRuta)).thenReturn(Optional.of(RepRutaFixture.ruta(idRuta,
                    RepSucursalFixture.QUETZALTENANGO, RepSucursalFixture.GUATEMALA)));
            when(rutaRepository.findById(inexistente)).thenReturn(Optional.empty());

            // Act & Assert
            assertAll(
                    () -> assertTrue(alcance.rutaDeSucursal(null, RepSucursalFixture.QUETZALTENANGO).isEmpty()),
                    () -> assertTrue(alcance.rutaDeSucursal(idRuta, RepSucursalFixture.QUETZALTENANGO).isPresent()),
                    () -> assertTrue(alcance.rutaDeSucursal(idRuta, null).isPresent()),
                    () -> assertThrows(SinPermisoSucursalException.class,
                            () -> alcance.rutaDeSucursal(idRuta, RepSucursalFixture.GUATEMALA)),
                    () -> assertThrows(RutaNoEncontradaException.class,
                            () -> alcance.rutaDeSucursal(inexistente, null))
            );
        }
    }
}
