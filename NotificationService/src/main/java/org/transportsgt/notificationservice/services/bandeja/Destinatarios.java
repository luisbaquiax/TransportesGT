package org.transportsgt.notificationservice.services.bandeja;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.payload.identidad.Rol;
import org.transportsgt.notificationservice.exception.ContactoNoDisponibleException;
import org.transportsgt.notificationservice.models.Contacto;
import org.transportsgt.notificationservice.repositories.ContactoRepository;

import java.util.List;
import java.util.UUID;

/**
 * Resuelve los destinatarios a partir de la copia local de contactos.
 */
@Component
@RequiredArgsConstructor
public class Destinatarios {

    private final ContactoRepository contactoRepository;

    /**
     * El usuario de la operación (comprador, cliente, dueño de la cartera). Se le escribe aunque esté inactivo: el
     * correo informa una operación que sí ocurrió (p. ej. un reembolso).
     *
     * @throws ContactoNoDisponibleException si su UsuarioCreado aún no llegó (error transitorio: se reintenta)
     */
    public Destinatario usuario(UUID idUsuario) {
        return contactoRepository.findById(idUsuario)
                .map(Destinatarios::de)
                .orElseThrow(() -> new ContactoNoDisponibleException(idUsuario));
    }

    /**
     * Administradores de sucursal ACTIVOS.
     */
    public List<Destinatario> adminsDeSucursal(UUID idSucursal) {
        return contactoRepository.findAllByIdSucursalAndRolAndActivoTrue(idSucursal, Rol.ADMIN_SUCURSAL).stream()
                .map(Destinatarios::de)
                .toList();
    }

    private static Destinatario de(Contacto contacto) {
        return new Destinatario(contacto.getIdUsuario(), contacto.getCorreo(), contacto.getNombreCompleto());
    }
}
