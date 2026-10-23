package org.transportsgt.tripservice.services.proyeccion;

import org.transportsgt.eventos.payload.DatosBus;
import org.transportsgt.eventos.payload.DatosChofer;
import org.transportsgt.eventos.payload.DatosSucursal;

/**
 * Réplicas locales (_ref) de sucursales, buses y choferes. Idempotentes por versión: una versión que no es mayor
 * que la aplicada se ignora. Una versión nula cuenta como 0.
 */
public interface ProyeccionReferenciasService {

    void aplicarSucursal(DatosSucursal datos, Long versionAgregado);

    void aplicarBus(DatosBus datos, Long versionAgregado);

    void aplicarChofer(DatosChofer datos, Long versionAgregado);
}
