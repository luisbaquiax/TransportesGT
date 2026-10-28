package org.transportsgt.notificationservice.services.bitacora.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.transportsgt.eventos.payload.identidad.Rol;
import org.transportsgt.notificationservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.notificationservice.dto.response.NotificacionResumenResponse;
import org.transportsgt.notificationservice.dto.response.PaginaResponse;
import org.transportsgt.notificationservice.dto.response.RecordatorioLicenciaResponse;
import org.transportsgt.notificationservice.exception.NotificacionNoEncontradaException;
import org.transportsgt.notificationservice.exception.RangoFechasInvalidoException;
import org.transportsgt.notificationservice.exception.ReintentoNoPermitidoException;
import org.transportsgt.notificationservice.exception.SinPermisoSucursalException;
import org.transportsgt.notificationservice.fixture.ChoferLicenciaFixture;
import org.transportsgt.notificationservice.fixture.ContactoFixture;
import org.transportsgt.notificationservice.fixture.PropiedadesFixture;
import org.transportsgt.notificationservice.mappers.NotificacionMapperImpl;
import org.transportsgt.notificationservice.mappers.RecordatorioMapperImpl;
import org.transportsgt.notificationservice.models.EstadoNotificacion;
import org.transportsgt.notificationservice.models.EstadoRecordatorio;
import org.transportsgt.notificationservice.models.Notificacion;
import org.transportsgt.notificationservice.models.RecordatorioLicencia;
import org.transportsgt.notificationservice.models.TipoNotificacion;
import org.transportsgt.notificationservice.repositories.NotificacionRepository;
import org.transportsgt.notificationservice.repositories.NotificacionSpecs;
import org.transportsgt.notificationservice.repositories.RecordatorioLicenciaRepository;
import org.transportsgt.notificationservice.repositories.RecordatorioSpecs;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BitacoraYLicenciasTest {

    @Nested
    class Bitacora {

        @Mock
        private NotificacionRepository notificacionRepository;
        @Mock
        private NotificacionSpecs specs;

        private BitacoraServiceImpl servicio;
        private Notificacion fallida;
        private Specification<Notificacion> filtro;

        @BeforeEach
        void setUp() {
            servicio = new BitacoraServiceImpl(notificacionRepository, specs, new NotificacionMapperImpl(),
                    PropiedadesFixture.fechas());
            fallida = Notificacion.builder().id(UUID.randomUUID()).tipo(TipoNotificacion.COMPRA_BOLETOS)
                    .correoDestino("ana@gmail.com").asunto("Compra").cuerpo("<html>cuerpo</html>")
                    .estado(EstadoNotificacion.FALLIDA).intentos((short) 5).ultimoError("SMTP caído")
                    .claveDedup("evt:x:ana@gmail.com").build();
            filtro = (root, query, cb) -> null;
        }

        @Test
        void listar_cuandoHayRangoDeDias_loConvierteAGuatemalaYOrdenaRecientesPrimero() {
            // Arrange
            when(specs.filtrar(EstadoNotificacion.FALLIDA, TipoNotificacion.COMPRA_BOLETOS,
                    OffsetDateTime.parse("2026-10-01T00:00-06:00"), OffsetDateTime.parse("2026-10-21T00:00-06:00")))
                    .thenReturn(filtro);
            when(notificacionRepository.findAll(eq(filtro), any(Pageable.class)))
                    .thenAnswer(i -> new PageImpl<>(List.of(fallida), i.getArgument(1), 1));
            ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);

            // Act
            PaginaResponse<NotificacionResumenResponse> resultado = servicio.listar(EstadoNotificacion.FALLIDA,
                    TipoNotificacion.COMPRA_BOLETOS, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-20"), 0, 20);

            // Assert
            verify(notificacionRepository).findAll(eq(filtro), pagina.capture());
            assertAll(
                    () -> assertEquals(1, resultado.totalElementos()),
                    () -> assertEquals("SMTP caído", resultado.contenido().getFirst().ultimoError()),
                    () -> assertEquals(Sort.Direction.DESC, pagina.getValue().getSort().getOrderFor("creadoEn").getDirection())
            );
        }

        @Test
        void listar_sinFechas_noLimitaElRango() {
            // Arrange
            when(specs.filtrar(null, null, null, null)).thenReturn(filtro);
            when(notificacionRepository.findAll(eq(filtro), any(Pageable.class)))
                    .thenAnswer(i -> new PageImpl<>(List.of(), i.getArgument(1), 0));

            // Act & Assert
            assertEquals(0, servicio.listar(null, null, null, null, 0, 20).totalElementos());
        }

        @Test
        void listar_cuandoDesdeEsPosteriorAHasta_lanzaRangoInvalido() {
            // Act & Assert
            assertThrows(RangoFechasInvalidoException.class, () -> servicio.listar(null, null,
                    LocalDate.parse("2026-10-21"), LocalDate.parse("2026-10-20"), 0, 20));
            verifyNoInteractions(notificacionRepository);
        }

        @Test
        void detalle_cuandoExiste_incluyeElCuerpo() {
            // Arrange
            when(notificacionRepository.findById(fallida.getId())).thenReturn(Optional.of(fallida));

            // Act & Assert
            assertEquals("<html>cuerpo</html>", servicio.detalle(fallida.getId()).cuerpo());
        }

        @Test
        void detalle_cuandoNoExiste_lanzaNoEncontrada() {
            // Arrange
            UUID id = UUID.randomUUID();
            when(notificacionRepository.findById(id)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(NotificacionNoEncontradaException.class, () -> servicio.detalle(id));
        }

        @Test
        void reintentar_cuandoEstaFallida_laDejaPendienteConCeroIntentos() {
            // Arrange
            when(notificacionRepository.findById(fallida.getId())).thenReturn(Optional.of(fallida));
            when(notificacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            // Act
            NotificacionResumenResponse respuesta = servicio.reintentar(fallida.getId());

            // Assert
            assertAll(
                    () -> assertEquals(EstadoNotificacion.PENDIENTE, respuesta.estado()),
                    () -> assertEquals((short) 0, respuesta.intentos()),
                    () -> assertEquals(OffsetDateTime.parse("2026-10-20T10:00-06:00"), respuesta.proximoIntentoEn())
            );
        }

        @Test
        void reintentar_cuandoNoEstaFallidaOEsUnEnlace_lanzaConflicto() {
            // Arrange
            Notificacion enviada = Notificacion.builder().id(UUID.randomUUID()).tipo(TipoNotificacion.COMPRA_BOLETOS)
                    .estado(EstadoNotificacion.ENVIADA).build();
            Notificacion enlace = Notificacion.builder().id(UUID.randomUUID()).tipo(TipoNotificacion.ENLACE_CONTRASENA)
                    .estado(EstadoNotificacion.FALLIDA).build();
            when(notificacionRepository.findById(enviada.getId())).thenReturn(Optional.of(enviada));
            when(notificacionRepository.findById(enlace.getId())).thenReturn(Optional.of(enlace));

            // Act & Assert
            assertAll(
                    () -> assertEquals("Solo se reintenta un correo FALLIDA (estado actual: ENVIADA)",
                            assertThrows(ReintentoNoPermitidoException.class,
                                    () -> servicio.reintentar(enviada.getId())).getMessage()),
                    () -> assertThrows(ReintentoNoPermitidoException.class, () -> servicio.reintentar(enlace.getId()))
            );
            verify(notificacionRepository, never()).save(any());
        }
    }

    @Nested
    class Licencias {

        @Mock
        private RecordatorioLicenciaRepository recordatorioRepository;
        @Mock
        private RecordatorioSpecs specs;

        private ConsultaLicenciasServiceImpl servicio;
        private RecordatorioLicencia aviso;
        private Specification<RecordatorioLicencia> filtro;

        @BeforeEach
        void setUp() {
            servicio = new ConsultaLicenciasServiceImpl(recordatorioRepository, specs, new RecordatorioMapperImpl());
            aviso = ChoferLicenciaFixture.aviso(ChoferLicenciaFixture.chofer(UUID.randomUUID()));
            filtro = (root, query, cb) -> null;
        }

        private void conResultados(UUID idSucursal, EstadoRecordatorio estado) {
            when(specs.filtrar(idSucursal, estado)).thenReturn(filtro);
            when(recordatorioRepository.findAll(eq(filtro), any(Pageable.class)))
                    .thenAnswer(i -> new PageImpl<>(List.of(aviso), i.getArgument(1), 1));
        }

        @Test
        void listar_comoAdminDeSistema_usaElFiltroPedido() {
            // Arrange
            conResultados(null, EstadoRecordatorio.PENDIENTE);
            UsuarioAutenticado sistema = ContactoFixture.usuario(Rol.ADMIN_SISTEMA, null);

            // Act
            PaginaResponse<RecordatorioLicenciaResponse> pagina = servicio.listar(sistema, null,
                    EstadoRecordatorio.PENDIENTE, 0, 20);

            // Assert
            RecordatorioLicenciaResponse fila = pagina.contenido().getFirst();
            assertAll(
                    () -> assertEquals("Juan Pérez", fila.nombreChofer()),
                    () -> assertEquals(ContactoFixture.SUCURSAL_XELA, fila.idSucursal()),
                    () -> assertEquals((short) 30, fila.diasAnticipacion()),
                    () -> assertEquals(ChoferLicenciaFixture.VENCIMIENTO, fila.fechaVencimiento())
            );
        }

        @Test
        void listar_comoAdminDeSucursal_seLimitaASuSucursal() {
            // Arrange
            conResultados(ContactoFixture.SUCURSAL_XELA, null);
            UsuarioAutenticado admin = ContactoFixture.usuario(Rol.ADMIN_SUCURSAL, ContactoFixture.SUCURSAL_XELA);

            // Act & Assert
            assertAll(
                    () -> assertEquals(1, servicio.listar(admin, null, null, 0, 20).totalElementos()),
                    () -> assertEquals(1, servicio.listar(admin, ContactoFixture.SUCURSAL_XELA, null, 0, 20)
                            .totalElementos())
            );
        }

        @Test
        void listar_cuandoElAdminDeSucursalPideOtraOSuTokenNoTraeSucursal_lanzaSinPermiso() {
            // Arrange
            UsuarioAutenticado admin = ContactoFixture.usuario(Rol.ADMIN_SUCURSAL, ContactoFixture.SUCURSAL_XELA);
            UsuarioAutenticado sinSucursal = ContactoFixture.usuario(Rol.ADMIN_SUCURSAL, null);

            // Act & Assert
            assertAll(
                    () -> assertThrows(SinPermisoSucursalException.class,
                            () -> servicio.listar(admin, ContactoFixture.SUCURSAL_CAPITAL, null, 0, 20)),
                    () -> assertThrows(SinPermisoSucursalException.class,
                            () -> servicio.listar(sinSucursal, null, null, 0, 20))
            );
            verifyNoInteractions(recordatorioRepository);
        }
    }
}
