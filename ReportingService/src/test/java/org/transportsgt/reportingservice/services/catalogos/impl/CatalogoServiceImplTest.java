package org.transportsgt.reportingservice.services.catalogos.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.reportingservice.config.security.UsuarioAutenticado;
import org.transportsgt.reportingservice.dto.response.catalogos.OpcionCatalogo;
import org.transportsgt.reportingservice.fixture.RepSucursalFixture;
import org.transportsgt.reportingservice.repositories.catalogos.CatalogoRepository;
import org.transportsgt.reportingservice.services.comun.AlcanceSucursal;
import org.transportsgt.reportingservice.support.Soporte;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogoServiceImplTest {

    @Mock
    private CatalogoRepository repository;
    @Mock
    private AlcanceSucursal alcance;

    private CatalogoServiceImpl servicio;
    private UsuarioAutenticado adminXela;

    @BeforeEach
    void setUp() {
        servicio = new CatalogoServiceImpl(repository, alcance);
        adminXela = Soporte.adminSucursal(RepSucursalFixture.QUETZALTENANGO);
    }

    @Nested
    class Sucursales {

        @Test
        void sucursales_convierteLasOpciones() {
            // Arrange
            when(repository.sucursales()).thenReturn(List.of(RepSucursalFixture.opcion("Quetzaltenango", true),
                    RepSucursalFixture.opcion("Cobán", null)));

            // Act
            List<OpcionCatalogo> opciones = servicio.sucursales();

            // Assert
            assertAll(
                    () -> assertEquals("Quetzaltenango", opciones.getFirst().nombre()),
                    () -> assertTrue(opciones.getFirst().activo()),
                    () -> assertFalse(opciones.get(1).activo())
            );
        }
    }

    @Nested
    class RutasYBuses {

        @Test
        void rutasYBuses_usanLaSucursalQueResuelveElAlcance() {
            // Arrange
            when(alcance.sucursalDelCatalogo(adminXela, null)).thenReturn(RepSucursalFixture.QUETZALTENANGO);
            when(repository.rutas(RepSucursalFixture.QUETZALTENANGO))
                    .thenReturn(List.of(RepSucursalFixture.opcion("Quetzaltenango → Ciudad de Guatemala", false)));
            when(repository.buses(RepSucursalFixture.QUETZALTENANGO))
                    .thenReturn(List.of(RepSucursalFixture.opcion("P900ABC (Toyota Hiace)", true)));

            // Act
            List<OpcionCatalogo> rutas = servicio.rutas(adminXela, null);
            List<OpcionCatalogo> buses = servicio.buses(adminXela, null);

            // Assert
            assertAll(
                    () -> assertFalse(rutas.getFirst().activo()),
                    () -> assertEquals("P900ABC (Toyota Hiace)", buses.getFirst().nombre())
            );
        }
    }
}
