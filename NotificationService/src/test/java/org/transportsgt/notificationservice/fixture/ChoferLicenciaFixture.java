package org.transportsgt.notificationservice.fixture;

import org.transportsgt.notificationservice.models.ChoferLicencia;
import org.transportsgt.notificationservice.models.EstadoRecordatorio;
import org.transportsgt.notificationservice.models.RecordatorioLicencia;

import java.time.LocalDate;
import java.util.UUID;

public final class ChoferLicenciaFixture {

    /** Hoy en Guatemala con el {@code Clock} de las pruebas ({@link EventoFixture#AHORA}). */
    public static final LocalDate HOY = LocalDate.parse("2026-10-20");
    public static final LocalDate VENCIMIENTO = LocalDate.parse("2027-03-15");

    private ChoferLicenciaFixture() {
    }

    public static ChoferLicencia chofer(UUID idChofer) {
        return ChoferLicencia.builder().idChofer(idChofer).idSucursal(ContactoFixture.SUCURSAL_XELA)
                .nombreCompleto("Juan Pérez").correo("juan.perez@transportes.gt").fechaVencimientoLicencia(VENCIMIENTO)
                .activo(true).version(0L).build();
    }

    /** Aviso PENDIENTE de 30 días para la fecha vigente del chofer, programado para hoy. */
    public static RecordatorioLicencia aviso(ChoferLicencia chofer) {
        return RecordatorioLicencia.builder().id(UUID.randomUUID()).chofer(chofer)
                .fechaVencimiento(chofer.getFechaVencimientoLicencia()).diasAnticipacion((short) 30)
                .fechaEnvioProgramada(HOY).estado(EstadoRecordatorio.PENDIENTE).intentos((short) 0).build();
    }
}
