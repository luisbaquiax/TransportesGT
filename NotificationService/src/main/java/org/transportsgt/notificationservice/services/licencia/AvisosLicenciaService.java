package org.transportsgt.notificationservice.services.licencia;

/**
 * Avisos de vencimiento de licencia (catálogo §4.5).
 */
public interface AvisosLicenciaService {

    /**
     * Procesa los avisos PENDIENTE cuya fecha programada es hoy o anterior (hora de Guatemala), cada uno en su propia
     * transacción.
     */
    ResumenAvisos procesar();
}
