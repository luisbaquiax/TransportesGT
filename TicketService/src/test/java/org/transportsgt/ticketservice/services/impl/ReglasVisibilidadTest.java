package org.transportsgt.ticketservice.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.ticketservice.fixture.ClienteRefFixture;
import org.transportsgt.ticketservice.fixture.CompraFixture;
import org.transportsgt.ticketservice.fixture.ViajeRefFixture;
import org.transportsgt.ticketservice.models.ClienteRef;
import org.transportsgt.ticketservice.models.Compra;
import org.transportsgt.ticketservice.models.ViajeRef;
import org.transportsgt.ticketservice.repositories.ClienteRefRepository;
import org.transportsgt.eventos.payload.Rol;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReglasVisibilidadTest {

    @Mock
    private ClienteRefRepository clienteRefRepository;

    private ReglasVisibilidad reglas;
    private ViajeRef viaje;
    private UUID idComprador;

    @BeforeEach
    void setUp() {
        reglas = new ReglasVisibilidad(clienteRefRepository);
        viaje = ViajeRefFixture.programado(UUID.randomUUID());
        idComprador = UUID.randomUUID();
    }

    @Nested
    class NitDe {

        @Test
        void nitDe_cuandoElPerfilTieneNitReal_loDevuelve() {
            // Arrange
            when(clienteRefRepository.findById(idComprador)).thenReturn(Optional.of(ClienteRefFixture.cliente(idComprador)));

            // Act & Assert
            assertEquals(Optional.of(ClienteRefFixture.NIT), reglas.nitDe(ClienteRefFixture.comprador(idComprador)));
        }

        @Test
        void nitDe_cuandoNoHayPerfilNiNitONitEsCF_devuelveVacio() {
            // Arrange
            UUID sinPerfil = UUID.randomUUID();
            UUID sinNit = UUID.randomUUID();
            UUID conCF = UUID.randomUUID();
            ClienteRef cf = ClienteRefFixture.cliente(conCF);
            cf.setNit("CF");
            when(clienteRefRepository.findById(sinPerfil)).thenReturn(Optional.empty());
            when(clienteRefRepository.findById(sinNit)).thenReturn(Optional.of(ClienteRefFixture.sinNit(sinNit)));
            when(clienteRefRepository.findById(conCF)).thenReturn(Optional.of(cf));

            // Act & Assert
            assertAll(
                    () -> assertTrue(reglas.nitDe(ClienteRefFixture.comprador(sinPerfil)).isEmpty()),
                    () -> assertTrue(reglas.nitDe(ClienteRefFixture.comprador(sinNit)).isEmpty()),
                    () -> assertTrue(reglas.nitDe(ClienteRefFixture.comprador(conCF)).isEmpty())
            );
        }
    }

    @Nested
    class PuedeVer {

        @Test
        void puedeVer_elAdministradorDeSistemaVeTodas() {
            // Arrange
            Compra compra = CompraFixture.confirmada(UUID.randomUUID(), viaje, idComprador, 1);

            // Act & Assert
            assertTrue(reglas.puedeVer(ClienteRefFixture.usuario(UUID.randomUUID(), Rol.ADMIN_SISTEMA, null), compra));
        }

        @Test
        void puedeVer_cajeroYAdministradorDeSucursalSoloLasDeSuSucursal() {
            // Arrange
            Compra compra = CompraFixture.confirmada(UUID.randomUUID(), viaje, idComprador, 1);
            when(clienteRefRepository.findById(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());

            // Act & Assert
            assertAll(
                    () -> assertTrue(reglas.puedeVer(ClienteRefFixture.usuario(UUID.randomUUID(), Rol.CAJERO,
                            ViajeRefFixture.SUCURSAL_XELA), compra)),
                    () -> assertTrue(reglas.puedeVer(ClienteRefFixture.usuario(UUID.randomUUID(), Rol.ADMIN_SUCURSAL,
                            ViajeRefFixture.SUCURSAL_XELA), compra)),
                    () -> assertFalse(reglas.puedeVer(ClienteRefFixture.usuario(UUID.randomUUID(), Rol.CAJERO,
                            ViajeRefFixture.SUCURSAL_CAPITAL), compra))
            );
        }

        @Test
        void puedeVer_elCompradorVeLasSuyasYLasDeMostradorConSuNit() {
            // Arrange
            Compra suya = CompraFixture.confirmada(UUID.randomUUID(), viaje, idComprador, 1);
            Compra mostradorConSuNit = CompraFixture.deMostrador(UUID.randomUUID(), viaje, UUID.randomUUID(), 2);
            Compra ajena = CompraFixture.deMostrador(UUID.randomUUID(), viaje, UUID.randomUUID(), 3);
            ajena.setNitComprador("9999999");
            when(clienteRefRepository.findById(idComprador)).thenReturn(Optional.of(ClienteRefFixture.cliente(idComprador)));

            // Act & Assert
            assertAll(
                    () -> assertTrue(reglas.puedeVer(ClienteRefFixture.comprador(idComprador), suya)),
                    () -> assertTrue(reglas.puedeVer(ClienteRefFixture.comprador(idComprador), mostradorConSuNit)),
                    () -> assertFalse(reglas.puedeVer(ClienteRefFixture.comprador(idComprador), ajena))
            );
        }
    }
}
