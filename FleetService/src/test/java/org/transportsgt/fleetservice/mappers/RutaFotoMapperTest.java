package org.transportsgt.fleetservice.mappers;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.transportsgt.fleetservice.models.Bus;
import org.transportsgt.fleetservice.models.Chofer;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * La ruta de la foto nunca expone la referencia interna y cambia con la versión del registro.
 */
class RutaFotoMapperTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final BusMapper busMapper = Mappers.getMapper(BusMapper.class);
    private final ChoferMapper choferMapper = Mappers.getMapper(ChoferMapper.class);

    @Test
    void bus_sinFoto_devuelveNull() {
        assertNull(busMapper.rutaFoto(Bus.builder().id(ID).build()));
        assertNull(busMapper.rutaFoto(Bus.builder().id(ID).fotoUrl(" ").build()));
    }

    @Test
    void bus_conFoto_usaLaVersionOCero() {
        assertEquals("/buses/" + ID + "/foto?v=3",
                busMapper.rutaFoto(Bus.builder().id(ID).fotoUrl("buses/x.jpg").version(3L).build()));
        assertEquals("/buses/" + ID + "/foto?v=0",
                busMapper.rutaFoto(Bus.builder().id(ID).fotoUrl("buses/x.jpg").build()));
    }

    @Test
    void chofer_sinFoto_devuelveNull() {
        assertNull(choferMapper.rutaFoto(Chofer.builder().id(ID).build()));
        assertNull(choferMapper.rutaFoto(Chofer.builder().id(ID).fotoUrl("").build()));
    }

    @Test
    void chofer_conFoto_usaLaVersionOCero() {
        assertEquals("/choferes/" + ID + "/foto?v=2",
                choferMapper.rutaFoto(Chofer.builder().id(ID).fotoUrl("choferes/x.jpg").version(2L).build()));
        assertEquals("/choferes/" + ID + "/foto?v=0",
                choferMapper.rutaFoto(Chofer.builder().id(ID).fotoUrl("choferes/x.jpg").build()));
    }
}
