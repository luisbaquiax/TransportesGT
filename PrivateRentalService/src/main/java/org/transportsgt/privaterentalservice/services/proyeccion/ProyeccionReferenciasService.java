package org.transportsgt.privaterentalservice.services.proyeccion;

import org.transportsgt.eventos.payload.DatosPerfilClienteActualizado;
import org.transportsgt.eventos.payload.DatosSucursal;

/**
 * Réplicas locales (_ref) de sucursales y perfiles de cliente. Idempotentes por versión: una versión que no es mayor
 * que la aplicada se ignora. Una versión nula cuenta como 0.
 */
public interface ProyeccionReferenciasService {

    void aplicarSucursal(DatosSucursal datos, Long versionAgregado);

    void aplicarCliente(DatosPerfilClienteActualizado datos, Long versionAgregado);
}
