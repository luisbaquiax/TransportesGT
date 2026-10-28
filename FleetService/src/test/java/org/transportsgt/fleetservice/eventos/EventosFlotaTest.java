package org.transportsgt.fleetservice.eventos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.mensajeria.publicacion.RegistroOutbox;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.flota.DatosBus;
import org.transportsgt.eventos.payload.flota.DatosChofer;
import org.transportsgt.fleetservice.mappers.BusMapper;
import org.transportsgt.fleetservice.mappers.ChoferMapper;
import org.transportsgt.fleetservice.models.Bus;
import org.transportsgt.fleetservice.models.Chofer;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventosFlotaTest {

    @Mock
    private RegistroOutbox outbox;
    @Mock
    private BusMapper busMapper;
    @Mock
    private ChoferMapper choferMapper;

    @InjectMocks
    private EventosFlota eventos;

    @Test
    void busRegistradoUsaElTipoYElIdCorrectos() {
        UUID id = UUID.randomUUID();
        Bus bus = Bus.builder().id(id).version(0L).kilometrajeActual(BigDecimal.ZERO).build();
        DatosBus datos = new DatosBus(id, null, "P1", "M", "Mod", 2019, 45, null, true);
        when(busMapper.toDatos(bus)).thenReturn(datos);

        eventos.busRegistrado(bus);

        verify(outbox).registrar(TipoEvento.BUS_REGISTRADO, id, 0L, datos);
    }

    @Test
    void busActualizadoUsaElMismoPayload() {
        UUID id = UUID.randomUUID();
        Bus bus = Bus.builder().id(id).version(2L).build();
        DatosBus datos = new DatosBus(id, null, "P1", "M", "Mod", 2019, 45, null, true);
        when(busMapper.toDatos(bus)).thenReturn(datos);

        eventos.busActualizado(bus);

        verify(outbox).registrar(TipoEvento.BUS_ACTUALIZADO, id, 2L, datos);
    }

    @Test
    void choferRegistradoUsaElTipoYElIdCorrectos() {
        UUID id = UUID.randomUUID();
        Chofer chofer = Chofer.builder().id(id).version(0L).build();
        DatosChofer datos = new DatosChofer(id, null, null, "Nombre", "c@t.gt", "5555", "LIC",
                org.transportsgt.eventos.payload.flota.TipoLicencia.A, java.time.LocalDate.of(2027, 1, 1),
                new BigDecimal("150.00"), true);
        when(choferMapper.toDatos(chofer)).thenReturn(datos);

        eventos.choferRegistrado(chofer);

        verify(outbox).registrar(TipoEvento.CHOFER_REGISTRADO, id, 0L, datos);
    }

    @Test
    void choferActualizadoUsaElMismoPayload() {
        UUID id = UUID.randomUUID();
        Chofer chofer = Chofer.builder().id(id).version(5L).build();
        DatosChofer datos = new DatosChofer(id, null, null, "Nombre", "c@t.gt", "5555", "LIC",
                org.transportsgt.eventos.payload.flota.TipoLicencia.A, java.time.LocalDate.of(2027, 1, 1),
                new BigDecimal("150.00"), true);
        when(choferMapper.toDatos(chofer)).thenReturn(datos);

        eventos.choferActualizado(chofer);

        verify(outbox).registrar(TipoEvento.CHOFER_ACTUALIZADO, id, 5L, datos);
    }
}
