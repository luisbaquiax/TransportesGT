package org.transportsgt.fleetservice.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TraductorErroresBdTest {

    private final TraductorErroresBd traductor = new TraductorErroresBd();

    @Test
    void buscarSqlException_devuelveLaMasInternaConSqlState() {
        // Arrange
        SQLException interna = new SQLException("interna", "23505");
        SQLException externa = new SQLException("externa", "23000", interna);
        SQLException sinEstado = new SQLException("sin estado");
        sinEstado.initCause(externa);

        // Act
        SQLException encontrada = traductor.buscarSqlException(new DataIntegrityViolationException("x", sinEstado));

        // Assert
        assertSame(interna, encontrada);
    }

    @Test
    void buscarSqlException_sinSqlException_devuelveNull() {
        assertNull(traductor.buscarSqlException(new IllegalStateException("x", new RuntimeException("y"))));
        assertNull(traductor.buscarSqlException(null));
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULO", value = {
            "'Key (lower(placa)) violates uq_bus_placa', Ya existe un bus con esa placa",
            "'violates chofer_numero_licencia_key', Ya existe un chofer con ese número de licencia",
            "'violates chofer_id_usuario_key', Ese usuario ya está enlazado a otro chofer",
            "'otra_restriccion', Ya existe un registro con esos datos",
            "NULO, Ya existe un registro con esos datos"
    })
    void mensajeUnico_traduceLaRestriccion(String detalle, String esperado) {
        assertEquals(esperado, traductor.mensajeUnico(detalle));
    }

    @Test
    void mensajeTrigger_quitaElPrefijoErrorYElContexto() {
        assertEquals("Los registros de bus no se eliminan",
                traductor.mensajeTrigger("ERROR: Los registros de bus no se eliminan\n  Where: PL/pgSQL function"));
    }

    @Test
    void mensajeTrigger_sinPrefijo_devuelveLaPrimeraLinea() {
        assertEquals("Mensaje simple", traductor.mensajeTrigger("  Mensaje simple  \nresto"));
    }

    @Test
    void mensajeTrigger_vacioONulo_devuelveMensajeGenerico() {
        assertEquals("La operación viola una regla de datos", traductor.mensajeTrigger(null));
        assertEquals("La operación viola una regla de datos", traductor.mensajeTrigger("  "));
    }
}
