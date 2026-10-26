package org.transportsgt.reportingservice.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.transportsgt.eventos.payload.EstadoOperativo;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.reportingservice.config.security.UsuarioAutenticado;
import org.transportsgt.reportingservice.controllers.reportsadmin.ReportesAdminController;
import org.transportsgt.reportingservice.controllers.reportssucursal.ReportesSucursalController;
import org.transportsgt.reportingservice.dto.request.FiltroFechas;
import org.transportsgt.reportingservice.dto.request.FormatoReporte;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.catalogos.OpcionCatalogo;
import org.transportsgt.reportingservice.exception.FormatoNoSoportadoException;
import org.transportsgt.reportingservice.exception.IntervaloInvalidoException;
import org.transportsgt.reportingservice.fixture.RepSucursalFixture;
import org.transportsgt.reportingservice.services.catalogos.CatalogoService;
import org.transportsgt.reportingservice.services.comun.RespuestaReporte;
import org.transportsgt.reportingservice.services.comun.ValidadorFiltros;
import org.transportsgt.reportingservice.services.reportsadmin.ReporteGananciasService;
import org.transportsgt.reportingservice.services.reportsadmin.ReporteGastosService;
import org.transportsgt.reportingservice.services.reportsadmin.ReporteGastosTallerCategoriaService;
import org.transportsgt.reportingservice.services.reportsadmin.ReporteIngresosService;
import org.transportsgt.reportingservice.services.reportsadmin.ReporteMapaRutasService;
import org.transportsgt.reportingservice.services.reportsadmin.ReporteRutasDemandadasService;
import org.transportsgt.reportingservice.services.reportssucursal.ReporteBusesService;
import org.transportsgt.reportingservice.services.reportssucursal.ReporteChoferesService;
import org.transportsgt.reportingservice.services.reportssucursal.ReporteDepreciacionBusesService;
import org.transportsgt.reportingservice.services.reportssucursal.ReporteGastosTallerBusService;
import org.transportsgt.reportingservice.services.reportssucursal.ReporteIngresosAlquileresService;
import org.transportsgt.reportingservice.services.reportssucursal.ReporteIngresosBoletosService;
import org.transportsgt.reportingservice.support.Soporte;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class ReportesControllersTest {

    @Mock
    private RespuestaReporte respuesta;
    @Mock
    private ReporteIngresosService ingresos;
    @Mock
    private ReporteGastosService gastos;
    @Mock
    private ReporteGananciasService ganancias;
    @Mock
    private ReporteRutasDemandadasService rutas;
    @Mock
    private ReporteGastosTallerCategoriaService tallerCategoria;
    @Mock
    private ReporteMapaRutasService mapa;
    @Mock
    private ReporteBusesService buses;
    @Mock
    private ReporteChoferesService choferes;
    @Mock
    private ReporteIngresosBoletosService boletos;
    @Mock
    private ReporteIngresosAlquileresService alquileres;
    @Mock
    private ReporteDepreciacionBusesService depreciacion;
    @Mock
    private ReporteGastosTallerBusService tallerBus;
    @Mock
    private CatalogoService catalogo;

    private ReportesAdminController admin;
    private ReportesSucursalController sucursal;
    private CatalogosController catalogos;
    private LocalDate desde;
    private LocalDate hasta;
    private ReporteTabla reporte;
    private ResponseEntity ok;
    private Jwt jwtXela;

    @BeforeEach
    void setUp() {
        ValidadorFiltros validador = new ValidadorFiltros();
        admin = new ReportesAdminController(validador, respuesta, ingresos, gastos, ganancias, rutas, tallerCategoria,
                mapa);
        sucursal = new ReportesSucursalController(validador, respuesta, buses, choferes, boletos, alquileres,
                depreciacion, tallerBus);
        catalogos = new CatalogosController(catalogo);
        desde = LocalDate.parse("2026-10-01");
        hasta = LocalDate.parse("2026-10-31");
        reporte = new ReporteTabla<>("R", null, null, List.of(), java.util.Map.of());
        ok = ResponseEntity.ok(reporte);
        jwtXela = Soporte.jwt(Rol.ADMIN_SUCURSAL, RepSucursalFixture.QUETZALTENANGO);
    }

    @Nested
    class Admin {

        @Test
        void reportesDelSistema_validanYDelegan() {
            // Arrange
            FiltroFechas intervalo = new FiltroFechas(desde, hasta);
            UUID idSucursal = RepSucursalFixture.QUETZALTENANGO;
            UUID idBus = UUID.randomUUID();
            when(ingresos.generar(intervalo, idSucursal)).thenReturn(reporte);
            when(gastos.generar(intervalo, idSucursal)).thenReturn(reporte);
            when(ganancias.generar(intervalo, null)).thenReturn(reporte);
            when(rutas.generar(intervalo)).thenReturn(reporte);
            when(tallerCategoria.generar(intervalo, idSucursal, idBus)).thenReturn(reporte);
            when(mapa.generar(intervalo, idSucursal)).thenReturn(reporte);
            when(respuesta.responder(eq(reporte), any(), any())).thenReturn(ok);

            // Act & Assert
            assertAll(
                    () -> assertSame(ok, admin.ingresos(desde, hasta, idSucursal, null)),
                    () -> assertSame(ok, admin.gastos(desde, hasta, idSucursal, "json")),
                    () -> assertSame(ok, admin.ganancias(desde, hasta, null, null)),
                    () -> assertSame(ok, admin.rutasDemandadas(desde, hasta, null)),
                    () -> assertSame(ok, admin.gastosTallerPorCategoria(desde, hasta, idSucursal, idBus, null)),
                    () -> assertSame(ok, admin.mapaRutas(desde, hasta, idSucursal, "pdf"))
            );
            verify(respuesta).responder(reporte, FormatoReporte.JSON, ReporteIngresosService.NOMBRE);
            verify(respuesta).responder(reporte, FormatoReporte.PDF, ReporteMapaRutasService.NOMBRE);
        }

        @Test
        void reportesDelSistema_desdePosteriorOFormatoInvalido_noConsultan() {
            assertAll(
                    () -> assertThrows(IntervaloInvalidoException.class, () -> admin.ingresos(hasta, desde, null, null)),
                    () -> assertThrows(FormatoNoSoportadoException.class, () -> admin.gastos(null, null, null, "csv"))
            );
            verifyNoInteractions(ingresos, gastos);
        }
    }

    @Nested
    class Sucursal {

        @Test
        void reportesDeSucursal_pasanElUsuarioDelToken() {
            // Arrange
            FiltroFechas todo = new FiltroFechas(null, null);
            UUID idRuta = UUID.randomUUID();
            UUID idBus = UUID.randomUUID();
            ArgumentCaptor<UsuarioAutenticado> usuario = ArgumentCaptor.forClass(UsuarioAutenticado.class);
            when(buses.generar(usuario.capture(), eq(null), eq(todo), eq(EstadoOperativo.DISPONIBLE))).thenReturn(reporte);
            when(choferes.generar(any(), eq(null), eq(todo))).thenReturn(reporte);
            when(boletos.generar(any(), eq(null), eq(todo), eq(idRuta), eq(idBus))).thenReturn(reporte);
            when(alquileres.generar(any(), eq(null), eq(todo))).thenReturn(reporte);
            when(depreciacion.generar(any(), eq(null), eq(todo))).thenReturn(reporte);
            when(tallerBus.generar(any(), eq(null), eq(todo), eq(idBus))).thenReturn(reporte);
            when(respuesta.responder(eq(reporte), any(), any())).thenReturn(ok);

            // Act & Assert
            assertAll(
                    () -> assertSame(ok, sucursal.buses(jwtXela, null, null, null, EstadoOperativo.DISPONIBLE, null)),
                    () -> assertSame(ok, sucursal.choferes(jwtXela, null, null, null, null)),
                    () -> assertSame(ok, sucursal.ingresosBoletos(jwtXela, null, null, null, idRuta, idBus, null)),
                    () -> assertSame(ok, sucursal.ingresosAlquileres(jwtXela, null, null, null, null)),
                    () -> assertSame(ok, sucursal.depreciacionBuses(jwtXela, null, null, null, null)),
                    () -> assertSame(ok, sucursal.gastosTallerPorBus(jwtXela, null, null, null, idBus, null)),
                    () -> assertEquals(RepSucursalFixture.QUETZALTENANGO, usuario.getValue().idSucursal()),
                    () -> assertEquals(Rol.ADMIN_SUCURSAL, usuario.getValue().rol())
            );
        }

        @Test
        void reportesDeSucursal_desdePosterior_lanza400() {
            assertThrows(IntervaloInvalidoException.class,
                    () -> sucursal.choferes(jwtXela, hasta, desde, null, null));
            verifyNoInteractions(choferes);
        }
    }

    @Nested
    class Catalogos {

        @Test
        void catalogos_delegan() {
            // Arrange
            List<OpcionCatalogo> opciones = List.of(new OpcionCatalogo(UUID.randomUUID(), "Xela", true));
            Jwt sistema = Soporte.jwt(Rol.ADMIN_SISTEMA, null);
            when(catalogo.sucursales()).thenReturn(opciones);
            when(catalogo.rutas(any(), eq(RepSucursalFixture.GUATEMALA))).thenReturn(opciones);
            when(catalogo.buses(any(), eq(null))).thenReturn(opciones);

            // Act & Assert
            assertAll(
                    () -> assertSame(opciones, catalogos.sucursales()),
                    () -> assertSame(opciones, catalogos.rutas(sistema, RepSucursalFixture.GUATEMALA)),
                    () -> assertSame(opciones, catalogos.buses(jwtXela, null))
            );
        }
    }
}
