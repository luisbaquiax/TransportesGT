package org.transportsgt.ticketservice.eventos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.ticketservice.models.Boleto;
import org.transportsgt.ticketservice.models.Compra;
import org.transportsgt.eventos.RegistroOutbox;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosBoletosAnulados;
import org.transportsgt.eventos.payload.DatosVentaBoletos;

import java.util.UUID;

/**
 * Arma los payloads de Boletos y los guarda en {@code outbox_evento} con la librería de eventos.
 *
 */
@Component
@RequiredArgsConstructor
public class EventosBoletos {

    private final RegistroOutbox outbox;

    /**
     * Una compra confirmada (en línea o de mostrador) = un evento con todos sus asientos.
     */
    public void ventaRegistrada(Compra compra, UUID idRuta) {
        DatosVentaBoletos datos = new DatosVentaBoletos(
                compra.getId(),
                compra.getIdViaje(),
                idRuta,
                compra.getCanal(),
                compra.getBoletos().stream().map(Boleto::getNumeroAsiento).map(Short::intValue).sorted().toList(),
                compra.getPrecioUnitario(),
                compra.getMontoTotal(),
                compra.getIdUsuarioComprador(),
                compra.getNitComprador(),
                compra.getNombreComprador(),
                compra.getIdPago(),
                compra.getFechaPago(),
                compra.getIdSucursalIngreso(),
                compra.getIdUsuarioVendedor());
        outbox.registrar(TipoEvento.VENTA_BOLETOS_REGISTRADA, compra.getId(), compra.getVersion(), datos);
    }

    /**
     * Una compra anulada = un evento. Cartera reembolsa si es EN_LINEA con pago; Reportes revierte el ingreso.
     */
    public void boletosAnulados(Compra compra) {
        DatosBoletosAnulados datos = new DatosBoletosAnulados(
                compra.getId(),
                compra.getIdViaje(),
                compra.getMotivoAnulacion(),
                compra.getCanal(),
                compra.getMontoTotal(),
                compra.getIdUsuarioComprador(),
                compra.getIdPago(),
                compra.getIdSucursalIngreso());
        outbox.registrar(TipoEvento.BOLETOS_ANULADOS, compra.getId(), compra.getVersion(), datos);
    }
}
