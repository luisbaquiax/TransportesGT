package org.transportsgt.notificationservice.services.proyeccion.impl;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalendarioRecordatoriosTest {

    private final CalendarioRecordatorios calendario = new CalendarioRecordatorios();
    private final LocalDate hoy = LocalDate.parse("2026-10-20");

    @Nested
    class Avisos {

        @Test
        void avisos_cuandoElVencimientoEstaLejos_programaLosTres() {
            // Act
            List<CalendarioRecordatorios.Aviso> avisos = calendario.avisos(LocalDate.parse("2027-03-15"), hoy);

            // Assert
            assertEquals(List.of(
                    new CalendarioRecordatorios.Aviso((short) 30, LocalDate.parse("2027-02-13")),
                    new CalendarioRecordatorios.Aviso((short) 15, LocalDate.parse("2027-02-28")),
                    new CalendarioRecordatorios.Aviso((short) 3, LocalDate.parse("2027-03-12"))), avisos);
        }

        @Test
        void avisos_cuandoElDeTreintaYaPaso_omiteSoloEse() {
            // Act: vence en 20 días → el de 30 días cayó hace 10
            List<CalendarioRecordatorios.Aviso> avisos = calendario.avisos(hoy.plusDays(20), hoy);

            // Assert
            assertEquals(List.of((short) 15, (short) 3), avisos.stream().map(CalendarioRecordatorios.Aviso::diasAnticipacion).toList());
        }

        @Test
        void avisos_cuandoUnoCaeHoy_loProgramaParaHoy() {
            // Act
            List<CalendarioRecordatorios.Aviso> avisos = calendario.avisos(hoy.plusDays(15), hoy);

            // Assert
            assertEquals(new CalendarioRecordatorios.Aviso((short) 15, hoy), avisos.getFirst());
        }

        @Test
        void avisos_cuandoLaLicenciaYaVenceEnMenosDeTresDias_noProgramaNinguno() {
            // Act & Assert
            assertTrue(calendario.avisos(hoy.plusDays(2), hoy).isEmpty());
        }
    }
}
