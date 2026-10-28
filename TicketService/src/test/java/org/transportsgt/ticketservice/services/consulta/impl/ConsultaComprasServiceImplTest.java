package org.transportsgt.ticketservice.services.consulta.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.transportsgt.ticketservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.ticketservice.dto.response.PaginaResponse;
import org.transportsgt.ticketservice.dto.response.CompraResponse;
import org.transportsgt.ticketservice.exception.CompraNoEncontradaException;
import org.transportsgt.ticketservice.fixture.ClienteRefFixture;
import org.transportsgt.ticketservice.fixture.CompraFixture;
import org.transportsgt.ticketservice.fixture.ViajeRefFixture;
import org.transportsgt.ticketservice.mappers.CompraMapperImpl;
import org.transportsgt.ticketservice.models.Compra;
import org.transportsgt.ticketservice.models.EstadoCompra;
import org.transportsgt.ticketservice.models.ViajeRef;
import org.transportsgt.ticketservice.repositories.CompraRepository;
import org.transportsgt.ticketservice.repositories.CompraSpecs;
import org.transportsgt.eventos.payload.identidad.Rol;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultaComprasServiceImplTest {

    @Mock
    private CompraRepository compraRepository;
    @Mock
    private CompraSpecs specs;
    @Mock
    private ReglasVisibilidad visibilidad;

    private ConsultaComprasServiceImpl servicio;
    private UUID idUsuario;
    private UsuarioAutenticado usuario;
    private ViajeRef viaje;
    private Compra compra;

    @BeforeEach
    void setUp() {
        servicio = new ConsultaComprasServiceImpl(compraRepository, specs, visibilidad, new CompraMapperImpl());
        idUsuario = UUID.randomUUID();
        usuario = ClienteRefFixture.comprador(idUsuario);
        viaje = ViajeRefFixture.programado(UUID.randomUUID());
        compra = CompraFixture.confirmada(UUID.randomUUID(), viaje, idUsuario, 12);
    }

    @Nested
    class Mias {

        @Test
        void mias_cuandoElPerfilTieneNit_buscaPorUsuarioYPorNit() {
            // Arrange
            when(visibilidad.nitDe(usuario)).thenReturn(Optional.of(ClienteRefFixture.NIT));
            when(compraRepository.findHistorial(eq(idUsuario), eq(ClienteRefFixture.NIT), isNull(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(compra), PageRequest.of(0, 20), 1));

            // Act
            PaginaResponse<CompraResponse> pagina = servicio.mias(usuario, null, 0, 20);

            // Assert
            assertAll(
                    () -> assertEquals(1, pagina.totalElementos()),
                    () -> assertEquals(compra.getId(), pagina.contenido().getFirst().idCompra())
            );
        }

        @Test
        void mias_cuandoElPerfilNoTieneNit_buscaSoloPorUsuario() {
            // Arrange
            when(visibilidad.nitDe(usuario)).thenReturn(Optional.empty());
            when(compraRepository.findHistorialSinNit(eq(idUsuario), isNull(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 5), 0));

            // Act
            PaginaResponse<CompraResponse> pagina = servicio.mias(usuario, null, 1, 5);

            // Assert
            assertEquals(1, pagina.pagina());
        }
    }

    @Nested
    class Detalle {

        @Test
        void detalle_cuandoLePermiteVerla_laDevuelve() {
            // Arrange
            when(compraRepository.findConBoletosById(compra.getId())).thenReturn(Optional.of(compra));
            when(visibilidad.puedeVer(usuario, compra)).thenReturn(true);

            // Act & Assert
            assertEquals(compra.getId(), servicio.detalle(usuario, compra.getId()).idCompra());
        }

        @Test
        void detalle_cuandoNoLeCorrespondeONoExiste_lanzaCompraNoEncontrada() {
            // Arrange
            UUID inexistente = UUID.randomUUID();
            when(compraRepository.findConBoletosById(compra.getId())).thenReturn(Optional.of(compra));
            when(compraRepository.findConBoletosById(inexistente)).thenReturn(Optional.empty());
            when(visibilidad.puedeVer(usuario, compra)).thenReturn(false);

            // Act & Assert
            assertAll(
                    () -> assertThrows(CompraNoEncontradaException.class, () -> servicio.detalle(usuario, compra.getId())),
                    () -> assertThrows(CompraNoEncontradaException.class, () -> servicio.detalle(usuario, inexistente))
            );
        }
    }

    @Nested
    class Listado {

        @Test
        void listado_filtraPorLaSucursalDelUsuarioYOrdenaRecientesPrimero() {
            // Arrange
            UsuarioAutenticado cajero = ClienteRefFixture.usuario(UUID.randomUUID(), Rol.CAJERO, ViajeRefFixture.SUCURSAL_XELA);
            Specification<Compra> filtro = (root, query, cb) -> null;
            when(specs.filtrar(ViajeRefFixture.SUCURSAL_XELA, viaje.getId(), EstadoCompra.CONFIRMADA)).thenReturn(filtro);
            when(compraRepository.findAll(eq(filtro), any(Pageable.class)))
                    .thenAnswer(i -> new PageImpl<>(List.of(compra), i.getArgument(1), 1));
            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

            // Act
            PaginaResponse<CompraResponse> pagina = servicio.listado(cajero, viaje.getId(), EstadoCompra.CONFIRMADA, 0, 10);

            // Assert
            verify(compraRepository).findAll(eq(filtro), captor.capture());
            assertAll(
                    () -> assertEquals(1, pagina.contenido().size()),
                    () -> assertEquals(Sort.Direction.DESC, captor.getValue().getSort().getOrderFor("creadoEn").getDirection())
            );
        }
    }
}
