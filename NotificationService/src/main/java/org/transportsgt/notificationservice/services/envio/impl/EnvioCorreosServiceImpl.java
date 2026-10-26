package org.transportsgt.notificationservice.services.envio.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.transportsgt.notificationservice.config.NotificacionesProperties;
import org.transportsgt.notificationservice.services.envio.EnvioCorreosService;
import org.transportsgt.notificationservice.services.envio.ResumenEnvio;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * No es transaccional: cada correo va en su propia transacción ({@link GestorEnvio}), así un fallo no afecta a los
 * demás. Un correo que falla se reprograma a futuro, por eso no se vuelve a tomar en la misma corrida.
 */
@Service
@RequiredArgsConstructor
public class EnvioCorreosServiceImpl implements EnvioCorreosService {

    private final GestorEnvio gestor;
    private final NotificacionesProperties propiedades;

    @Override
    public ResumenEnvio procesar() {
        Map<GestorEnvio.Resultado, Integer> conteo = new EnumMap<>(GestorEnvio.Resultado.class);
        for (int i = 0; i < propiedades.envio().lote(); i++) {
            Optional<GestorEnvio.Resultado> resultado = gestor.enviarSiguiente();
            if (resultado.isEmpty()) {
                break;
            }
            conteo.merge(resultado.get(), 1, Integer::sum);
        }
        return new ResumenEnvio(conteo.getOrDefault(GestorEnvio.Resultado.ENVIADA, 0),
                conteo.getOrDefault(GestorEnvio.Resultado.REPROGRAMADA, 0),
                conteo.getOrDefault(GestorEnvio.Resultado.FALLIDA, 0));
    }
}
