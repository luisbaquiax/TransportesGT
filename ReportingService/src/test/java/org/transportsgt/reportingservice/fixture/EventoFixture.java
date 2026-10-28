package org.transportsgt.reportingservice.fixture;

import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.flota.BusAsignado;
import org.transportsgt.eventos.payload.boletos.Canal;
import org.transportsgt.eventos.payload.flota.ChoferAsignado;
import org.transportsgt.eventos.payload.alquileres.DatosAlquilerCancelado;
import org.transportsgt.eventos.payload.alquileres.DatosAlquilerConfirmado;
import org.transportsgt.eventos.payload.boletos.DatosBoletosAnulados;
import org.transportsgt.eventos.payload.flota.DatosBus;
import org.transportsgt.eventos.payload.flota.DatosChofer;
import org.transportsgt.eventos.payload.costos.DatosDepreciacion;
import org.transportsgt.eventos.payload.costos.DatosGastoCombustible;
import org.transportsgt.eventos.payload.costos.DatosGastoTaller;
import org.transportsgt.eventos.payload.viajes.DatosLlegada;
import org.transportsgt.eventos.payload.costos.DatosPagoChofer;
import org.transportsgt.eventos.payload.viajes.DatosRuta;
import org.transportsgt.eventos.payload.viajes.DatosRutaEliminada;
import org.transportsgt.eventos.payload.viajes.DatosSalida;
import org.transportsgt.eventos.payload.identidad.DatosSucursal;
import org.transportsgt.eventos.payload.costos.DatosTarifaDepreciacion;
import org.transportsgt.eventos.payload.boletos.DatosVentaBoletos;
import org.transportsgt.eventos.payload.viajes.DatosViaje;
import org.transportsgt.eventos.payload.viajes.DatosViajeEliminado;
import org.transportsgt.eventos.payload.flota.EstadoOperativo;
import org.transportsgt.eventos.payload.viajes.EstadoViaje;
import org.transportsgt.eventos.payload.costos.LineaTaller;
import org.transportsgt.eventos.payload.flota.TipoLicencia;
import org.transportsgt.eventos.payload.costos.TipoLinea;
import org.transportsgt.eventos.payload.viajes.TipoViaje;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Sobres y payloads de los eventos que consume Reportes (catálogo v4).
 */
public final class EventoFixture {

    /** 05:30 UTC del 22 = 23:30 del 21 en Guatemala (UTC-6). */
    public static final Instant OCURRIDO_EN = Instant.parse("2026-10-22T05:30:00Z");
    public static final LocalDate FECHA_PAGO = LocalDate.parse("2026-10-20");
    public static final OffsetDateTime SALIDA = OffsetDateTime.parse("2026-10-25T06:00:00-06:00");
    public static final OffsetDateTime LLEGADA = OffsetDateTime.parse("2026-10-25T10:30:00-06:00");

    private EventoFixture() {
    }

    public static <T> SobreEvento<T> sobre(TipoEvento tipo, T datos, Long versionAgregado) {
        return new SobreEvento<>(UUID.randomUUID(), tipo.nombre(), 1, OCURRIDO_EN,
                tipo.productor().nombreServicio(), UUID.randomUUID(), versionAgregado, UUID.randomUUID(), datos);
    }

    public static <T> SobreEvento<T> sobre(TipoEvento tipo, T datos) {
        return sobre(tipo, datos, null);
    }

    public static DatosSucursal sucursal(UUID id, String nombre) {
        return new DatosSucursal(id, nombre, "Quetzaltenango", "Zona 1", new BigDecimal("14.834700"),
                new BigDecimal("-91.518100"), true);
    }

    public static DatosBus bus(UUID id, UUID idSucursal) {
        return new DatosBus(id, idSucursal, "P900ABC", "Toyota", "Hiace", 2019, 45, EstadoOperativo.DISPONIBLE, true);
    }

    public static DatosChofer chofer(UUID id, UUID idSucursal) {
        return new DatosChofer(id, null, idSucursal, "José Méndez", "jose@transportes.gt", "55555555", "LIC-1",
                TipoLicencia.A, LocalDate.parse("2027-03-15"), new BigDecimal("150.00"), true);
    }

    public static DatosRuta ruta(UUID id, UUID origen, UUID destino) {
        return new DatosRuta(id, origen, destino, new BigDecimal("205.50"), new BigDecimal("85.00"));
    }

    public static DatosRutaEliminada rutaEliminada(UUID id) {
        return new DatosRutaEliminada(id, OCURRIDO_EN);
    }

    public static DatosViaje viaje(UUID id, UUID idRuta, UUID idSucursal, UUID idBus, UUID idChofer, EstadoViaje estado) {
        return new DatosViaje(id, TipoViaje.REGULAR, estado, idRuta, idSucursal, "Quetzaltenango", "Ciudad de Guatemala",
                null, SALIDA, LLEGADA, new BigDecimal("85.00"),
                idBus == null ? null : new BusAsignado(idBus, "P900ABC", 45),
                idChofer == null ? null : new ChoferAsignado(idChofer, "José Méndez"));
    }

    public static DatosViajeEliminado viajeEliminado(UUID id) {
        return new DatosViajeEliminado(id, TipoViaje.REGULAR, null, OCURRIDO_EN, UUID.randomUUID());
    }

    public static DatosSalida salida(UUID idViaje, UUID idBus, String kilometraje) {
        return new DatosSalida(idViaje, idBus, UUID.randomUUID(), SALIDA, new BigDecimal(kilometraje), UUID.randomUUID());
    }

    public static DatosLlegada llegada(UUID idViaje, UUID idBus, String kmInicial, String kmFinal) {
        BigDecimal inicial = new BigDecimal(kmInicial);
        BigDecimal fin = new BigDecimal(kmFinal);
        return new DatosLlegada(idViaje, TipoViaje.REGULAR, null, UUID.randomUUID(), UUID.randomUUID(), idBus,
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("150.00"), SALIDA, LLEGADA, inicial, fin,
                fin.subtract(inicial), new BigDecimal("420.00"), UUID.randomUUID());
    }

    public static DatosVentaBoletos venta(UUID idCompra, UUID idSucursal, UUID idViaje, UUID idRuta, String monto,
                                          int asientos) {
        List<Integer> numeros = java.util.stream.IntStream.rangeClosed(1, asientos).boxed().toList();
        return new DatosVentaBoletos(idCompra, idViaje, idRuta, Canal.EN_LINEA, numeros, new BigDecimal("85.00"),
                new BigDecimal(monto), UUID.randomUUID(), "1234567K", "Ana López", UUID.randomUUID(), FECHA_PAGO,
                idSucursal, null);
    }

    public static DatosBoletosAnulados boletosAnulados(UUID idCompra) {
        return new DatosBoletosAnulados(idCompra, UUID.randomUUID(), "VIAJE_ELIMINADO", Canal.EN_LINEA,
                new BigDecimal("170.00"), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    public static DatosAlquilerConfirmado alquiler(UUID idAlquiler, UUID idSucursal, UUID idPago, String precio) {
        return new DatosAlquilerConfirmado(idAlquiler, UUID.randomUUID(), "Empresa Ñandú", "CF", idSucursal,
                "Quetzaltenango", "San Marcos", SALIDA, LLEGADA.plusDays(1), LLEGADA.plusDays(1), true, 30,
                new BigDecimal(precio), idPago, FECHA_PAGO);
    }

    public static DatosAlquilerCancelado alquilerCancelado(UUID idAlquiler, Instant canceladoEn) {
        return new DatosAlquilerCancelado(idAlquiler, UUID.randomUUID(), "Empresa Ñandú", "CF", UUID.randomUUID(),
                new BigDecimal("3800.00"), null, "VIAJE_ELIMINADO", canceladoEn);
    }

    public static DatosGastoCombustible combustible(UUID idGasto, UUID idSucursal, UUID idBus, String monto) {
        return new DatosGastoCombustible(idGasto, UUID.randomUUID(), idBus, idSucursal, new BigDecimal(monto),
                FECHA_PAGO);
    }

    public static DatosDepreciacion depreciacion(UUID id, UUID idSucursal, UUID idBus, String km, String monto) {
        return new DatosDepreciacion(id, UUID.randomUUID(), idBus, idSucursal, new BigDecimal(km),
                new BigDecimal("2.00"), new BigDecimal(monto), FECHA_PAGO);
    }

    public static LineaTaller linea(UUID idLinea, String categoria, TipoLinea tipo, String monto) {
        return new LineaTaller(idLinea, UUID.randomUUID(), categoria, "Detalle " + categoria, tipo, new BigDecimal(monto));
    }

    public static DatosGastoTaller taller(UUID idGasto, UUID idSucursal, UUID idBus, List<LineaTaller> lineas) {
        BigDecimal total = lineas.stream().map(LineaTaller::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DatosGastoTaller(idGasto, idBus, idSucursal, FECHA_PAGO, total, lineas);
    }

    public static DatosPagoChofer pagoChofer(UUID idPago, UUID idSucursal, UUID idViaje, String monto) {
        return new DatosPagoChofer(idPago, idViaje, UUID.randomUUID(), idSucursal, new BigDecimal("150.00"),
                new BigDecimal("1.00"), new BigDecimal(monto), FECHA_PAGO);
    }

    public static DatosTarifaDepreciacion tarifa(String montoPorKm, LocalDate vigenteDesde) {
        return new DatosTarifaDepreciacion(new BigDecimal(montoPorKm), vigenteDesde, UUID.randomUUID());
    }
}
