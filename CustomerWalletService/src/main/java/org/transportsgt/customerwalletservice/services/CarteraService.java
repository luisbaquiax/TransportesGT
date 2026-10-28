package org.transportsgt.customerwalletservice.services;

import org.transportsgt.customerwalletservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.customerwalletservice.dto.request.RecargaRequest;
import org.transportsgt.customerwalletservice.dto.response.CarteraResponse;
import org.transportsgt.customerwalletservice.dto.response.MovimientoResponse;
import org.transportsgt.customerwalletservice.dto.response.PaginaResponse;
import org.transportsgt.customerwalletservice.dto.response.Resultado;
import org.transportsgt.customerwalletservice.models.TipoMovimiento;

import java.time.LocalDate;

/**
 * Cartera del cliente desde el frontend: saldo, recargas y movimientos. Los cobros de la saga están en
 * {@link PagoService}.
 */
public interface CarteraService {

    CarteraResponse obtener(UsuarioAutenticado usuario);

    /**
     * Suma saldo. La misma {@code Clave-Idempotencia} con la misma cartera y monto devuelve la recarga original
     * ({@code creado = false}); con otra cartera u otro monto, 409. Publica CarteraRecargada.
     */
    Resultado<MovimientoResponse> recargar(UsuarioAutenticado usuario, String claveIdempotencia, RecargaRequest request);

    /** Libro de movimientos, el más reciente primero. Filtros opcionales. */
    PaginaResponse<MovimientoResponse> movimientos(UsuarioAutenticado usuario, TipoMovimiento tipo,
                                                   LocalDate desde, LocalDate hasta, int pagina, int tamano);
}
