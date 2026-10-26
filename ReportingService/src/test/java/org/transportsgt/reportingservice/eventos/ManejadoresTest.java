package org.transportsgt.reportingservice.eventos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.reportingservice.eventos.alquileres.ManejadorAlquilerCancelado;
import org.transportsgt.reportingservice.eventos.alquileres.ManejadorAlquilerConfirmado;
import org.transportsgt.reportingservice.eventos.boletos.ManejadorBoletosAnulados;
import org.transportsgt.reportingservice.eventos.boletos.ManejadorVentaBoletosRegistrada;
import org.transportsgt.reportingservice.eventos.costos.ManejadorDepreciacionCalculada;
import org.transportsgt.reportingservice.eventos.costos.ManejadorGastoCombustibleRegistrado;
import org.transportsgt.reportingservice.eventos.costos.ManejadorGastoTallerRegistrado;
import org.transportsgt.reportingservice.eventos.costos.ManejadorPagoChoferRegistrado;
import org.transportsgt.reportingservice.eventos.costos.ManejadorTarifaDepreciacionCambiada;
import org.transportsgt.reportingservice.eventos.flota.ManejadorBusActualizado;
import org.transportsgt.reportingservice.eventos.flota.ManejadorBusRegistrado;
import org.transportsgt.reportingservice.eventos.flota.ManejadorChoferActualizado;
import org.transportsgt.reportingservice.eventos.flota.ManejadorChoferRegistrado;
import org.transportsgt.reportingservice.eventos.identidad.ManejadorSucursalActualizada;
import org.transportsgt.reportingservice.eventos.identidad.ManejadorSucursalCreada;
import org.transportsgt.reportingservice.eventos.viajes.ManejadorLlegadaRegistrada;
import org.transportsgt.reportingservice.eventos.viajes.ManejadorRutaActualizada;
import org.transportsgt.reportingservice.eventos.viajes.ManejadorRutaCreada;
import org.transportsgt.reportingservice.eventos.viajes.ManejadorRutaEliminada;
import org.transportsgt.reportingservice.eventos.viajes.ManejadorSalidaRegistrada;
import org.transportsgt.reportingservice.eventos.viajes.ManejadorViajeActualizado;
import org.transportsgt.reportingservice.eventos.viajes.ManejadorViajeEliminado;
import org.transportsgt.reportingservice.eventos.viajes.ManejadorViajeProgramado;
import org.transportsgt.reportingservice.services.proyecciones.ProyeccionFlotaService;
import org.transportsgt.reportingservice.services.proyecciones.ProyeccionGastosService;
import org.transportsgt.reportingservice.services.proyecciones.ProyeccionIngresosService;
import org.transportsgt.reportingservice.services.proyecciones.ProyeccionSucursalService;
import org.transportsgt.reportingservice.services.proyecciones.ProyeccionViajesService;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

/**
 * Los manejadores solo delegan: cada uno declara su tipo y llama a su proyección con el mismo sobre.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class ManejadoresTest {

    /** Lo que consume Reportes según el catálogo v4 (§3): nunca identidad.usuario.*. */
    private static final Set<TipoEvento> CONSUMIDOS = EnumSet.of(
            TipoEvento.SUCURSAL_CREADA, TipoEvento.SUCURSAL_ACTUALIZADA,
            TipoEvento.BUS_REGISTRADO, TipoEvento.BUS_ACTUALIZADO, TipoEvento.CHOFER_REGISTRADO,
            TipoEvento.CHOFER_ACTUALIZADO, TipoEvento.RUTA_CREADA, TipoEvento.RUTA_ACTUALIZADA,
            TipoEvento.RUTA_ELIMINADA, TipoEvento.VIAJE_PROGRAMADO, TipoEvento.VIAJE_ACTUALIZADO,
            TipoEvento.VIAJE_ELIMINADO, TipoEvento.SALIDA_REGISTRADA, TipoEvento.LLEGADA_REGISTRADA,
            TipoEvento.VENTA_BOLETOS_REGISTRADA, TipoEvento.BOLETOS_ANULADOS, TipoEvento.ALQUILER_CONFIRMADO,
            TipoEvento.ALQUILER_CANCELADO, TipoEvento.GASTO_COMBUSTIBLE_REGISTRADO, TipoEvento.DEPRECIACION_CALCULADA,
            TipoEvento.GASTO_TALLER_REGISTRADO, TipoEvento.TARIFA_DEPRECIACION_CAMBIADA,
            TipoEvento.PAGO_CHOFER_REGISTRADO);

    @Mock
    private ProyeccionSucursalService sucursal;
    @Mock
    private ProyeccionFlotaService flota;
    @Mock
    private ProyeccionViajesService viajes;
    @Mock
    private ProyeccionIngresosService ingresos;
    @Mock
    private ProyeccionGastosService gastos;

    private List<ManejadorEvento<?>> manejadores;
    private SobreEvento sobre;

    @BeforeEach
    void setUp() {
        manejadores = List.of(new ManejadorSucursalCreada(sucursal), new ManejadorSucursalActualizada(sucursal),
                new ManejadorBusRegistrado(flota), new ManejadorBusActualizado(flota),
                new ManejadorChoferRegistrado(flota), new ManejadorChoferActualizado(flota),
                new ManejadorRutaCreada(viajes), new ManejadorRutaActualizada(viajes),
                new ManejadorRutaEliminada(viajes), new ManejadorViajeProgramado(viajes),
                new ManejadorViajeActualizado(viajes), new ManejadorViajeEliminado(viajes),
                new ManejadorSalidaRegistrada(viajes), new ManejadorLlegadaRegistrada(viajes),
                new ManejadorVentaBoletosRegistrada(ingresos), new ManejadorBoletosAnulados(ingresos),
                new ManejadorAlquilerConfirmado(ingresos), new ManejadorAlquilerCancelado(ingresos),
                new ManejadorGastoCombustibleRegistrado(gastos), new ManejadorDepreciacionCalculada(gastos),
                new ManejadorGastoTallerRegistrado(gastos), new ManejadorTarifaDepreciacionCambiada(gastos),
                new ManejadorPagoChoferRegistrado(gastos));
        sobre = new SobreEvento<>(UUID.randomUUID(), "X", 1, null, "s", UUID.randomUUID(), 1L, UUID.randomUUID(), null);
    }

    @Nested
    class Tipo {

        @Test
        void tipo_cubreExactamenteLosEventosDelCatalogoSinRepetir() {
            Set<TipoEvento> tipos = manejadores.stream().map(ManejadorEvento::tipo).collect(Collectors.toSet());
            assertAll(
                    () -> assertEquals(CONSUMIDOS, tipos),
                    () -> assertEquals(23, manejadores.size())
            );
        }
    }

    @Nested
    class Manejar {

        @Test
        void manejar_delegaCadaEventoEnSuProyeccion() {
            // Act
            manejadores.forEach(m -> ((ManejadorEvento) m).manejar(sobre));

            // Assert
            verify(sucursal, org.mockito.Mockito.times(2)).aplicarSucursal(sobre);
            verify(flota, org.mockito.Mockito.times(2)).aplicarBus(sobre);
            verify(flota, org.mockito.Mockito.times(2)).aplicarChofer(sobre);
            verify(viajes, org.mockito.Mockito.times(2)).aplicarRuta(sobre);
            verify(viajes).eliminarRuta(sobre);
            verify(viajes, org.mockito.Mockito.times(2)).aplicarViaje(sobre);
            verify(viajes).eliminarViaje(sobre);
            verify(viajes).registrarSalida(sobre);
            verify(viajes).registrarLlegada(sobre);
            verify(ingresos).registrarVenta(sobre);
            verify(ingresos).anularVenta(sobre);
            verify(ingresos).registrarAlquiler(sobre);
            verify(ingresos).cancelarAlquiler(sobre);
            verify(gastos).registrarCombustible(sobre);
            verify(gastos).registrarDepreciacion(sobre);
            verify(gastos).registrarTaller(sobre);
            verify(gastos).cambiarTarifa(sobre);
            verify(gastos).registrarPagoChofer(sobre);
        }
    }
}
