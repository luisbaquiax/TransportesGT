package org.transportsgt.tripservice.services.viaje.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.transportsgt.tripservice.exception.ReglaNegocioException;
import org.transportsgt.tripservice.models.Viaje;
import org.transportsgt.tripservice.repositories.ViajeRepository;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuardadoViajeTest {

    @Mock
    private ViajeRepository viajeRepository;

    @InjectMocks
    private GuardadoViaje guardado;

    private final Viaje viaje = Viaje.builder().build();

    @Test
    void guardaElViaje() {
        when(viajeRepository.saveAndFlush(viaje)).thenReturn(viaje);
        assertSame(viaje, guardado.guardar(viaje));
    }

    @Test
    void traslapeDePostgresSeTraduceAConflictoHorario() {
        SQLException exclusion = new SQLException("traslape", "23P01");
        when(viajeRepository.saveAndFlush(viaje)).thenThrow(
                new DataIntegrityViolationException("x", new RuntimeException("envuelta", exclusion)));

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class, () -> guardado.guardar(viaje));

        assertEquals("CONFLICTO_HORARIO", ex.getCodigo());
    }

    @Test
    void otrosErroresDeDatosSePropaganSinCambio() {
        DataIntegrityViolationException otro = new DataIntegrityViolationException("x",
                new SQLException("unico", "23505"));
        DataIntegrityViolationException sinSql = new DataIntegrityViolationException("sin causa");
        when(viajeRepository.saveAndFlush(viaje)).thenThrow(otro).thenThrow(sinSql);

        assertSame(otro, assertThrows(DataIntegrityViolationException.class, () -> guardado.guardar(viaje)));
        assertSame(sinSql, assertThrows(DataIntegrityViolationException.class, () -> guardado.guardar(viaje)));
    }
}
