package org.transportsgt.reportingservice.integracion;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.flota.DatosBus;
import org.transportsgt.eventos.payload.identidad.DatosSucursal;
import org.transportsgt.eventos.payload.flota.EstadoOperativo;
import org.transportsgt.eventos.payload.viajes.EstadoViaje;
import org.transportsgt.eventos.payload.costos.TipoLinea;
import org.transportsgt.reportingservice.fixture.EventoFixture;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cada evento consumido actualiza su proyección; duplicados sin efecto; versiones viejas ignoradas.
 */
class ConsumidoresIT extends IntegracionBase {

    private Map<String, Object> fila(String tabla, UUID id) {
        return jdbc.queryForMap("SELECT * FROM " + tabla + " WHERE id = ?", id);
    }

    private int filas(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private void publicarYEsperar(TipoEvento tipo, SobreEvento<?> sobre) {
        publicar(tipo, sobre);
        esperar(tipo.nombre() + " " + sobre.idEvento(), () -> eventoProcesado(sobre.idEvento()));
    }

    @Nested
    class Identidad {

        @Test
        void sucursal_creadaActualizadaDuplicadaYVersionVieja() {
            // Arrange
            UUID id = UUID.randomUUID();
            SobreEvento<DatosSucursal> creada = sobre(TipoEvento.SUCURSAL_CREADA, id, 1L,
                    EventoFixture.sucursal(id, "Mazatenango"));

            // Act
            publicarYEsperar(TipoEvento.SUCURSAL_CREADA, creada);
            publicar(TipoEvento.SUCURSAL_CREADA, creada);
            publicarYEsperar(TipoEvento.SUCURSAL_ACTUALIZADA, sobre(TipoEvento.SUCURSAL_ACTUALIZADA, id, 3L,
                    EventoFixture.sucursal(id, "Mazatenango Centro")));
            publicarYEsperar(TipoEvento.SUCURSAL_ACTUALIZADA, sobre(TipoEvento.SUCURSAL_ACTUALIZADA, id, 2L,
                    EventoFixture.sucursal(id, "Nombre viejo")));

            // Assert
            assertAll(
                    () -> assertEquals("Mazatenango Centro", fila("rep_sucursal", id).get("nombre")),
                    () -> assertEquals(3L, fila("rep_sucursal", id).get("version")),
                    () -> assertEquals(1, filas("SELECT count(*) FROM evento_procesado WHERE id_evento = ?",
                            creada.idEvento()))
            );
        }
    }

    @Nested
    class Flota {

        @Test
        void busYChofer_seProyectanSinTocarElKilometrajeConActualizado() {
            // Arrange
            UUID idBus = UUID.randomUUID();
            UUID idChofer = UUID.randomUUID();
            UUID idSucursal = UUID.randomUUID();
            publicarYEsperar(TipoEvento.BUS_REGISTRADO, sobre(TipoEvento.BUS_REGISTRADO, idBus, 1L,
                    EventoFixture.bus(idBus, idSucursal)));
            jdbc.update("UPDATE rep_bus SET kilometraje_actual = 5000 WHERE id = ?", idBus);

            // Act
            publicarYEsperar(TipoEvento.BUS_ACTUALIZADO, sobre(TipoEvento.BUS_ACTUALIZADO, idBus, 2L,
                    new DatosBus(idBus, idSucursal, "P900ABC", "Toyota", "Coaster", 2019, 30, EstadoOperativo.EN_TALLER,
                            true)));
            publicarYEsperar(TipoEvento.CHOFER_REGISTRADO, sobre(TipoEvento.CHOFER_REGISTRADO, idChofer, 1L,
                    EventoFixture.chofer(idChofer, idSucursal)));
            publicarYEsperar(TipoEvento.CHOFER_ACTUALIZADO, sobre(TipoEvento.CHOFER_ACTUALIZADO, idChofer, 0L,
                    EventoFixture.chofer(idChofer, UUID.randomUUID())));

            // Assert
            Map<String, Object> bus = fila("rep_bus", idBus);
            assertAll(
                    () -> assertEquals("EN_TALLER", bus.get("estado_operativo")),
                    () -> assertEquals("Coaster", bus.get("modelo")),
                    () -> assertEquals(0, new BigDecimal("5000.0").compareTo((BigDecimal) bus.get("kilometraje_actual"))),
                    () -> assertEquals(idSucursal, fila("rep_chofer", idChofer).get("id_sucursal")),
                    () -> assertEquals("LIC-1", fila("rep_chofer", idChofer).get("numero_licencia"))
            );
        }
    }

    @Nested
    class Viajes {

        @Test
        void rutaViajeSalidaYLlegada_actualizanViajeYBus() {
            // Arrange
            UUID idRuta = UUID.randomUUID();
            UUID idViaje = UUID.randomUUID();
            UUID idBus = UUID.randomUUID();
            UUID origen = UUID.randomUUID();
            publicarYEsperar(TipoEvento.BUS_REGISTRADO, sobre(TipoEvento.BUS_REGISTRADO, idBus, 1L,
                    EventoFixture.bus(idBus, origen)));
            publicarYEsperar(TipoEvento.RUTA_CREADA, sobre(TipoEvento.RUTA_CREADA, idRuta, 1L,
                    EventoFixture.ruta(idRuta, origen, UUID.randomUUID())));
            publicarYEsperar(TipoEvento.VIAJE_PROGRAMADO, sobre(TipoEvento.VIAJE_PROGRAMADO, idViaje, 1L,
                    EventoFixture.viaje(idViaje, idRuta, origen, idBus, UUID.randomUUID(), EstadoViaje.PROGRAMADO)));

            // Act: salida
            publicarYEsperar(TipoEvento.SALIDA_REGISTRADA, sobre(TipoEvento.SALIDA_REGISTRADA, idViaje, null,
                    EventoFixture.salida(idViaje, idBus, "1000.0")));
            String estadoEnRuta = (String) fila("rep_bus", idBus).get("estado_operativo");
            // Act: llegada y un ViajeActualizado viejo (EN_TRANSITO) que llega después
            publicarYEsperar(TipoEvento.LLEGADA_REGISTRADA, sobre(TipoEvento.LLEGADA_REGISTRADA, idViaje, null,
                    EventoFixture.llegada(idViaje, idBus, "1000.0", "1205.5")));
            publicarYEsperar(TipoEvento.VIAJE_ACTUALIZADO, sobre(TipoEvento.VIAJE_ACTUALIZADO, idViaje, 2L,
                    EventoFixture.viaje(idViaje, idRuta, origen, idBus, UUID.randomUUID(), EstadoViaje.EN_TRANSITO)));

            // Assert
            Map<String, Object> viaje = fila("rep_viaje", idViaje);
            Map<String, Object> bus = fila("rep_bus", idBus);
            assertAll(
                    () -> assertEquals("EN_TRANSITO", estadoEnRuta),
                    () -> assertEquals("FINALIZADO", viaje.get("estado")),
                    () -> assertEquals(0, new BigDecimal("205.5").compareTo((BigDecimal) viaje.get("distancia_recorrida_km"))),
                    () -> assertEquals("DISPONIBLE", bus.get("estado_operativo")),
                    () -> assertEquals(0, new BigDecimal("1205.5").compareTo((BigDecimal) bus.get("kilometraje_actual")))
            );
        }

        @Test
        void llegadaDesordenada_noBajaElKilometraje() {
            // Arrange
            UUID idBus = UUID.randomUUID();
            UUID viajeNuevo = UUID.randomUUID();
            UUID viajeViejo = UUID.randomUUID();
            UUID origen = UUID.randomUUID();
            publicarYEsperar(TipoEvento.BUS_REGISTRADO, sobre(TipoEvento.BUS_REGISTRADO, idBus, 1L,
                    EventoFixture.bus(idBus, origen)));
            for (UUID idViaje : List.of(viajeNuevo, viajeViejo)) {
                publicarYEsperar(TipoEvento.VIAJE_PROGRAMADO, sobre(TipoEvento.VIAJE_PROGRAMADO, idViaje, 1L,
                        EventoFixture.viaje(idViaje, null, origen, idBus, null, EstadoViaje.PROGRAMADO)));
            }

            // Act: llega primero la llegada del viaje más reciente
            publicarYEsperar(TipoEvento.LLEGADA_REGISTRADA, sobre(TipoEvento.LLEGADA_REGISTRADA, viajeNuevo, null,
                    EventoFixture.llegada(viajeNuevo, idBus, "2000.0", "2300.0")));
            publicarYEsperar(TipoEvento.LLEGADA_REGISTRADA, sobre(TipoEvento.LLEGADA_REGISTRADA, viajeViejo, null,
                    EventoFixture.llegada(viajeViejo, idBus, "1000.0", "1300.0")));

            // Assert
            assertEquals(0, new BigDecimal("2300.0").compareTo((BigDecimal) fila("rep_bus", idBus)
                    .get("kilometraje_actual")));
        }

        @Test
        void rutaYViajeEliminados_quedanMarcados() {
            // Arrange
            UUID idRuta = UUID.randomUUID();
            UUID idViaje = UUID.randomUUID();
            UUID origen = UUID.randomUUID();
            publicarYEsperar(TipoEvento.RUTA_CREADA, sobre(TipoEvento.RUTA_CREADA, idRuta, 1L,
                    EventoFixture.ruta(idRuta, origen, UUID.randomUUID())));
            publicarYEsperar(TipoEvento.VIAJE_PROGRAMADO, sobre(TipoEvento.VIAJE_PROGRAMADO, idViaje, 1L,
                    EventoFixture.viaje(idViaje, idRuta, origen, null, null, EstadoViaje.PROGRAMADO)));

            // Act
            publicarYEsperar(TipoEvento.RUTA_ELIMINADA, sobre(TipoEvento.RUTA_ELIMINADA, idRuta, null,
                    EventoFixture.rutaEliminada(idRuta)));
            publicarYEsperar(TipoEvento.VIAJE_ELIMINADO, sobre(TipoEvento.VIAJE_ELIMINADO, idViaje, null,
                    EventoFixture.viajeEliminado(idViaje)));

            // Assert
            assertAll(
                    () -> assertEquals(true, fila("rep_ruta", idRuta).get("eliminada")),
                    () -> assertEquals(true, fila("rep_viaje", idViaje).get("eliminado"))
            );
        }
    }

    @Nested
    class Ingresos {

        @Test
        void ventaDuplicadaYAlquilerCancelado_ingresosCorrectos() {
            // Arrange
            UUID idCompra = UUID.randomUUID();
            UUID idAlquiler = UUID.randomUUID();
            UUID idSucursal = UUID.randomUUID();
            SobreEvento<?> venta = sobre(TipoEvento.VENTA_BOLETOS_REGISTRADA, idCompra, null, EventoFixture.venta(
                    idCompra, idSucursal, UUID.randomUUID(), UUID.randomUUID(), "170.00", 2));
            SobreEvento<?> reenviada = sobre(TipoEvento.VENTA_BOLETOS_REGISTRADA, idCompra, null, EventoFixture.venta(
                    idCompra, idSucursal, UUID.randomUUID(), UUID.randomUUID(), "999.00", 5));

            // Act
            publicarYEsperar(TipoEvento.VENTA_BOLETOS_REGISTRADA, venta);
            publicarYEsperar(TipoEvento.VENTA_BOLETOS_REGISTRADA, reenviada);
            publicarYEsperar(TipoEvento.ALQUILER_CONFIRMADO, sobre(TipoEvento.ALQUILER_CONFIRMADO, idAlquiler, null,
                    EventoFixture.alquiler(idAlquiler, idSucursal, null, "3800.00")));
            publicarYEsperar(TipoEvento.ALQUILER_CANCELADO, sobre(TipoEvento.ALQUILER_CANCELADO, idAlquiler, null,
                    EventoFixture.alquilerCancelado(idAlquiler, null)));

            // Assert
            assertAll(
                    () -> assertEquals(1, filas("SELECT count(*) FROM rep_ingreso WHERE id_referencia = ?", idCompra)),
                    () -> assertEquals(0, new BigDecimal("170.00").compareTo(jdbc.queryForObject(
                            "SELECT monto FROM rep_ingreso WHERE id_referencia = ?", BigDecimal.class, idCompra))),
                    () -> assertEquals(2, filas("SELECT cantidad_boletos FROM rep_ingreso WHERE id_referencia = ?",
                            idCompra)),
                    () -> assertEquals(1, filas("""
                            SELECT count(*) FROM rep_ingreso WHERE id_referencia = ? AND anulado AND canal = 'MOSTRADOR'
                            """, idAlquiler)),
                    () -> assertEquals(0, filas("""
                            SELECT count(*) FROM v_resumen_diario_sucursal WHERE id_sucursal = ? AND ingresos = 3800
                            """, idSucursal))
            );
        }
    }

    @Nested
    class Costos {

        @Test
        void gastosYTarifa_seProyectanConFechaDeNegocio() {
            // Arrange
            UUID idSucursal = UUID.randomUUID();
            UUID idBus = UUID.randomUUID();
            UUID idViaje = UUID.randomUUID();
            LocalDate vigenteDesde = LocalDate.parse("2030-01-01");
            publicarYEsperar(TipoEvento.VIAJE_PROGRAMADO, sobre(TipoEvento.VIAJE_PROGRAMADO, idViaje, 1L,
                    EventoFixture.viaje(idViaje, null, idSucursal, idBus, null, EstadoViaje.PROGRAMADO)));

            // Act
            publicarYEsperar(TipoEvento.GASTO_COMBUSTIBLE_REGISTRADO, sobre(TipoEvento.GASTO_COMBUSTIBLE_REGISTRADO,
                    UUID.randomUUID(), null, EventoFixture.combustible(UUID.randomUUID(), idSucursal, idBus, "420.00")));
            publicarYEsperar(TipoEvento.DEPRECIACION_CALCULADA, sobre(TipoEvento.DEPRECIACION_CALCULADA,
                    UUID.randomUUID(), null, EventoFixture.depreciacion(UUID.randomUUID(), idSucursal, idBus, "205.5",
                            "411.00")));
            publicarYEsperar(TipoEvento.GASTO_TALLER_REGISTRADO, sobre(TipoEvento.GASTO_TALLER_REGISTRADO,
                    UUID.randomUUID(), null, EventoFixture.taller(UUID.randomUUID(), idSucursal, idBus, List.of(
                            EventoFixture.linea(UUID.randomUUID(), "Frenos", TipoLinea.REPUESTO, "850.00"),
                            EventoFixture.linea(UUID.randomUUID(), "Frenos", TipoLinea.MANO_DE_OBRA, "300.00")))));
            publicarYEsperar(TipoEvento.PAGO_CHOFER_REGISTRADO, sobre(TipoEvento.PAGO_CHOFER_REGISTRADO,
                    UUID.randomUUID(), null, EventoFixture.pagoChofer(UUID.randomUUID(), idSucursal, idViaje, "172.50")));
            publicarYEsperar(TipoEvento.TARIFA_DEPRECIACION_CAMBIADA, sobre(TipoEvento.TARIFA_DEPRECIACION_CAMBIADA,
                    UUID.randomUUID(), null, EventoFixture.tarifa("2.50", vigenteDesde)));
            publicarYEsperar(TipoEvento.TARIFA_DEPRECIACION_CAMBIADA, sobre(TipoEvento.TARIFA_DEPRECIACION_CAMBIADA,
                    UUID.randomUUID(), null, EventoFixture.tarifa("2.75", vigenteDesde)));

            // Assert
            assertAll(
                    () -> assertEquals(5, filas("SELECT count(*) FROM rep_gasto WHERE id_sucursal = ?", idSucursal)),
                    () -> assertEquals(2, filas("""
                            SELECT count(*) FROM rep_gasto WHERE id_sucursal = ? AND tipo_gasto = 'TALLER'
                            """, idSucursal)),
                    () -> assertEquals(idBus, jdbc.queryForObject("""
                            SELECT id_bus FROM rep_gasto WHERE id_sucursal = ? AND tipo_gasto = 'SALARIO_CHOFER'
                            """, UUID.class, idSucursal)),
                    () -> assertEquals(0, new BigDecimal("2153.50").compareTo(jdbc.queryForObject(
                            "SELECT sum(monto) FROM rep_gasto WHERE id_sucursal = ?", BigDecimal.class, idSucursal))),
                    () -> assertEquals(EventoFixture.FECHA_PAGO, jdbc.queryForObject(
                            "SELECT max(fecha_gasto) FROM rep_gasto WHERE id_sucursal = ?", LocalDate.class, idSucursal)),
                    () -> assertEquals(0, new BigDecimal("2.75").compareTo(jdbc.queryForObject(
                            "SELECT monto_por_km FROM rep_tarifa_depreciacion WHERE vigente_desde = ?", BigDecimal.class,
                            vigenteDesde))),
                    () -> assertTrue(filas("SELECT count(*) FROM rep_tarifa_depreciacion WHERE vigente_desde = ?",
                            vigenteDesde) == 1)
            );
        }
    }
}
