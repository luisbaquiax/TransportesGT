package org.transportsgt.customerwalletservice.services.impl;

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
import org.transportsgt.customerwalletservice.config.security.UsuarioAutenticado;
import org.transportsgt.customerwalletservice.dto.request.RecargaRequest;
import org.transportsgt.customerwalletservice.dto.response.CarteraResponse;
import org.transportsgt.customerwalletservice.dto.response.MovimientoResponse;
import org.transportsgt.customerwalletservice.dto.response.PaginaResponse;
import org.transportsgt.customerwalletservice.dto.response.Resultado;
import org.transportsgt.customerwalletservice.eventos.EventosCartera;
import org.transportsgt.customerwalletservice.exception.ClaveIdempotenciaRequeridaException;
import org.transportsgt.customerwalletservice.exception.ClaveIdempotenciaReutilizadaException;
import org.transportsgt.customerwalletservice.exception.ClienteNoEncontradoException;
import org.transportsgt.customerwalletservice.exception.CuentaInactivaException;
import org.transportsgt.customerwalletservice.exception.RangoFechasInvalidoException;
import org.transportsgt.customerwalletservice.fixture.CarteraFixture;
import org.transportsgt.customerwalletservice.fixture.ClienteFixture;
import org.transportsgt.customerwalletservice.mappers.CarteraMapperImpl;
import org.transportsgt.customerwalletservice.models.Cartera;
import org.transportsgt.customerwalletservice.models.Cliente;
import org.transportsgt.customerwalletservice.models.MovimientoCartera;
import org.transportsgt.customerwalletservice.models.TipoMovimiento;
import org.transportsgt.customerwalletservice.repositories.CarteraRepository;
import org.transportsgt.customerwalletservice.repositories.MovimientoCarteraRepository;
import org.transportsgt.customerwalletservice.repositories.MovimientoCarteraSpecs;
import org.transportsgt.customerwalletservice.utils.Dinero;
import org.transportsgt.eventos.payload.TipoReferencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CarteraServiceImplTest {

    private static final LocalDate FECHA = LocalDate.parse("2026-10-20");

    @Mock
    private CarteraRepository carteraRepository;
    @Mock
    private MovimientoCarteraRepository movimientoRepository;
    @Mock
    private EventosCartera eventos;
    @Mock
    private MovimientoCarteraSpecs specs;

    private CarteraServiceImpl servicio;
    private UUID idUsuario;
    private Cliente cliente;
    private Cartera cartera;
    private UsuarioAutenticado usuario;
    private String clave;
    private UUID idReferencia;

    @BeforeEach
    void setUp() {
        servicio = new CarteraServiceImpl(carteraRepository, movimientoRepository, new CarteraMapperImpl(), eventos,
                new ReglasCartera(new Dinero()), specs, new Dinero());
        idUsuario = UUID.randomUUID();
        cliente = ClienteFixture.cliente(idUsuario);
        cartera = CarteraFixture.cartera(cliente, "150.00");
        usuario = ClienteFixture.usuario(idUsuario);
        clave = UUID.randomUUID().toString();
        idReferencia = UUID.randomUUID();
    }

    @Nested
    class Obtener {

        @Test
        void obtener_cuandoExiste_devuelveSaldo() {
            // Arrange
            when(carteraRepository.findByClienteIdUsuario(idUsuario)).thenReturn(Optional.of(cartera));

            // Act
            CarteraResponse respuesta = servicio.obtener(usuario);

            // Assert
            assertAll(
                    () -> assertEquals(cartera.getId(), respuesta.idCartera()),
                    () -> assertEquals(new BigDecimal("150.00"), respuesta.saldo())
            );
        }

        @Test
        void obtener_cuandoNoExiste_lanzaClienteNoEncontrado() {
            // Arrange
            when(carteraRepository.findByClienteIdUsuario(idUsuario)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ClienteNoEncontradoException.class, () -> servicio.obtener(usuario));
        }
    }

    @Nested
    class Recargar {

        @Test
        void recargar_cuandoLaClaveEsNueva_sumaSaldoGuardaMovimientoYPublica() {
            // Arrange
            when(carteraRepository.findByIdUsuarioParaActualizar(idUsuario)).thenReturn(Optional.of(cartera));
            when(movimientoRepository.findByClaveIdempotencia(clave)).thenReturn(Optional.empty());
            when(movimientoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(carteraRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
            ArgumentCaptor<MovimientoCartera> captor = ArgumentCaptor.forClass(MovimientoCartera.class);

            // Act
            Resultado<MovimientoResponse> resultado = servicio.recargar(usuario, " " + clave + " ",
                    new RecargaRequest(new BigDecimal("200.5"), FECHA));

            // Assert
            verify(movimientoRepository).save(captor.capture());
            MovimientoCartera recarga = captor.getValue();
            assertAll(
                    () -> assertTrue(resultado.creado()),
                    () -> assertEquals(TipoMovimiento.RECARGA, recarga.getTipo()),
                    () -> assertEquals(new BigDecimal("200.50"), recarga.getMonto()),
                    () -> assertEquals(new BigDecimal("350.50"), recarga.getSaldoPosterior()),
                    () -> assertEquals(clave, recarga.getClaveIdempotencia()),
                    () -> assertEquals(FECHA, recarga.getFechaMovimiento()),
                    () -> assertNull(recarga.getTipoReferencia()),
                    () -> assertEquals(new BigDecimal("350.50"), cartera.getSaldo()),
                    () -> assertEquals(new BigDecimal("350.50"), resultado.valor().saldoPosterior())
            );
            verify(eventos).carteraRecargada(cartera, recarga);
        }

        @Test
        void recargar_cuandoLaClaveSeRepiteConElMismoMonto_devuelveLaOriginalSinSumar() {
            // Arrange
            MovimientoCartera original = CarteraFixture.recarga(cartera, "200.00", clave);
            when(carteraRepository.findByIdUsuarioParaActualizar(idUsuario)).thenReturn(Optional.of(cartera));
            when(movimientoRepository.findByClaveIdempotencia(clave)).thenReturn(Optional.of(original));

            // Act
            Resultado<MovimientoResponse> resultado = servicio.recargar(usuario, clave,
                    new RecargaRequest(new BigDecimal("200"), FECHA));

            // Assert
            assertAll(
                    () -> assertFalse(resultado.creado()),
                    () -> assertEquals(original.getId(), resultado.valor().idMovimiento()),
                    () -> assertEquals(new BigDecimal("150.00"), cartera.getSaldo())
            );
            verify(movimientoRepository, never()).save(any());
            verifyNoInteractions(eventos);
        }

        @Test
        void recargar_cuandoLaClaveSeRepiteConOtroMonto_lanzaClaveReutilizada() {
            // Arrange
            when(carteraRepository.findByIdUsuarioParaActualizar(idUsuario)).thenReturn(Optional.of(cartera));
            when(movimientoRepository.findByClaveIdempotencia(clave))
                    .thenReturn(Optional.of(CarteraFixture.recarga(cartera, "200.00", clave)));

            // Act & Assert
            assertThrows(ClaveIdempotenciaReutilizadaException.class, () -> servicio.recargar(usuario, clave,
                    new RecargaRequest(new BigDecimal("300.00"), FECHA)));
        }

        @Test
        void recargar_cuandoLaClaveEsDeOtraCartera_lanzaClaveReutilizada() {
            // Arrange
            Cartera otra = CarteraFixture.cartera(ClienteFixture.cliente(UUID.randomUUID()), "0.00");
            when(carteraRepository.findByIdUsuarioParaActualizar(idUsuario)).thenReturn(Optional.of(cartera));
            when(movimientoRepository.findByClaveIdempotencia(clave))
                    .thenReturn(Optional.of(CarteraFixture.recarga(otra, "200.00", clave)));

            // Act & Assert
            assertThrows(ClaveIdempotenciaReutilizadaException.class, () -> servicio.recargar(usuario, clave,
                    new RecargaRequest(new BigDecimal("200.00"), FECHA)));
        }

        @Test
        void recargar_cuandoLaClaveEsDeUnPago_lanzaClaveReutilizada() {
            // Arrange
            MovimientoCartera pago = CarteraFixture.pago(cartera, TipoReferencia.BOLETOS, idReferencia, "200.00");
            when(carteraRepository.findByIdUsuarioParaActualizar(idUsuario)).thenReturn(Optional.of(cartera));
            when(movimientoRepository.findByClaveIdempotencia(idReferencia.toString())).thenReturn(Optional.of(pago));

            // Act & Assert
            assertThrows(ClaveIdempotenciaReutilizadaException.class, () -> servicio.recargar(usuario,
                    idReferencia.toString(), new RecargaRequest(new BigDecimal("200.00"), FECHA)));
        }

        @Test
        void recargar_cuandoFaltaLaClave_lanzaClaveRequerida() {
            // Act & Assert
            assertAll(
                    () -> assertThrows(ClaveIdempotenciaRequeridaException.class, () -> servicio.recargar(usuario,
                            null, new RecargaRequest(BigDecimal.TEN, FECHA))),
                    () -> assertThrows(ClaveIdempotenciaRequeridaException.class, () -> servicio.recargar(usuario,
                            "  ", new RecargaRequest(BigDecimal.TEN, FECHA))),
                    () -> assertThrows(ClaveIdempotenciaRequeridaException.class,
                            () -> servicio.recargar(usuario, "x".repeat(ReglasCartera.LARGO_MAXIMO_CLAVE + 1),
                                    new RecargaRequest(BigDecimal.TEN, FECHA)))
            );
            verifyNoInteractions(carteraRepository, movimientoRepository);
        }

        @Test
        void recargar_cuandoLaCuentaEstaInactiva_lanzaCuentaInactiva() {
            // Arrange
            Cartera deInactivo = CarteraFixture.cartera(ClienteFixture.inactivo(idUsuario), "0.00");
            when(carteraRepository.findByIdUsuarioParaActualizar(idUsuario)).thenReturn(Optional.of(deInactivo));

            // Act & Assert
            assertThrows(CuentaInactivaException.class, () -> servicio.recargar(usuario, clave,
                    new RecargaRequest(BigDecimal.TEN, FECHA)));
            verifyNoInteractions(movimientoRepository);
        }

        @Test
        void recargar_cuandoNoHayCliente_lanzaClienteNoEncontrado() {
            // Arrange
            when(carteraRepository.findByIdUsuarioParaActualizar(idUsuario)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ClienteNoEncontradoException.class, () -> servicio.recargar(usuario, clave,
                    new RecargaRequest(BigDecimal.TEN, FECHA)));
        }
    }

    @Nested
    class Movimientos {

        @Test
        void movimientos_cuandoHayFiltros_pideEsosFiltrosYPaginaOrdenandoPorFechaDescendente() {
            // Arrange
            MovimientoCartera recarga = CarteraFixture.recarga(cartera, "200.00", clave);
            Specification<MovimientoCartera> filtro = (root, query, cb) -> null;
            when(carteraRepository.findByClienteIdUsuario(idUsuario)).thenReturn(Optional.of(cartera));
            when(specs.filtrar(cartera.getId(), TipoMovimiento.RECARGA, FECHA, FECHA)).thenReturn(filtro);
            when(movimientoRepository.findAll(eq(filtro), any(Pageable.class)))
                    .thenAnswer(i -> new PageImpl<>(List.of(recarga), i.getArgument(1), 1));
            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

            // Act
            PaginaResponse<MovimientoResponse> pagina = servicio.movimientos(usuario, TipoMovimiento.RECARGA,
                    FECHA, FECHA, 2, 5);

            // Assert
            verify(movimientoRepository).findAll(eq(filtro), captor.capture());
            Pageable pedido = captor.getValue();
            assertAll(
                    () -> assertEquals(2, pedido.getPageNumber()),
                    () -> assertEquals(5, pedido.getPageSize()),
                    () -> assertEquals(Sort.Direction.DESC, pedido.getSort().getOrderFor("fechaMovimiento").getDirection()),
                    () -> assertEquals(Sort.Direction.DESC, pedido.getSort().getOrderFor("creadoEn").getDirection()),
                    () -> assertEquals(1, pagina.contenido().size()),
                    () -> assertEquals(recarga.getId(), pagina.contenido().getFirst().idMovimiento())
            );
        }

        @Test
        void movimientos_cuandoDesdeEsPosteriorAHasta_lanzaRangoInvalido() {
            // Act & Assert
            assertThrows(RangoFechasInvalidoException.class, () -> servicio.movimientos(usuario, null,
                    FECHA.plusDays(1), FECHA, 0, 20));
            verifyNoInteractions(carteraRepository, movimientoRepository, specs);
        }

        @Test
        void movimientos_cuandoSoloHayUnLimite_noValidaElRango() {
            // Arrange
            Specification<MovimientoCartera> filtro = (root, query, cb) -> null;
            when(carteraRepository.findByClienteIdUsuario(idUsuario)).thenReturn(Optional.of(cartera));
            when(specs.filtrar(cartera.getId(), null, FECHA, null)).thenReturn(filtro);
            when(movimientoRepository.findAll(eq(filtro), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            // Act
            PaginaResponse<MovimientoResponse> pagina = servicio.movimientos(usuario, null, FECHA, null, 0, 20);

            // Assert
            assertEquals(0, pagina.totalElementos());
        }
    }
}
