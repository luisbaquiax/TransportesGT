-- bd_notificaciones — Notificaciones (modelo-datos-postgresql-v4.md)

-- Outbox transaccional: el evento se guarda junto con el cambio de negocio
CREATE TABLE outbox_evento (
    id               UUID PRIMARY KEY,                 -- = idEvento del sobre
    tipo_agregado    VARCHAR(50)  NOT NULL,
    id_agregado      UUID         NOT NULL,
    tipo_evento      VARCHAR(100) NOT NULL,
    llave_ruteo      VARCHAR(150) NOT NULL,
    version_agregado BIGINT,
    id_correlacion   UUID,
    datos            JSONB        NOT NULL,
    creado_en        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    publicado_en     TIMESTAMPTZ,
    intentos         INT          NOT NULL DEFAULT 0
);
CREATE INDEX idx_outbox_pendientes ON outbox_evento (creado_en) WHERE publicado_en IS NULL;
-- Job de limpieza (diario, PublicadorOutbox.limpiarPublicados): borra filas publicadas con más de 24 h

-- Consumidor idempotente: evita procesar dos veces el mismo evento
CREATE TABLE evento_procesado (
    id_evento    UUID         NOT NULL,
    consumidor   VARCHAR(100) NOT NULL,                -- nombre del listener
    procesado_en TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (id_evento, consumidor)
);

-- Tablas, índices y triggers

-- Copia local de contactos (alimentada por UsuarioCreado/Actualizado)
CREATE TABLE contacto (
    id_usuario       UUID PRIMARY KEY,
    correo           VARCHAR(150) NOT NULL,
    nombre_completo  VARCHAR(150) NOT NULL,
    rol              VARCHAR(20)  NOT NULL,
    id_sucursal      UUID,
    activo           BOOLEAN      NOT NULL,
    version          BIGINT       NOT NULL
);
CREATE INDEX idx_contacto_sucursal_rol ON contacto (id_sucursal, rol) WHERE activo;

-- Choferes con licencia (alimentada por ChoferRegistrado/Actualizado)
CREATE TABLE chofer_licencia (
    id_chofer                   UUID PRIMARY KEY,
    id_sucursal                 UUID         NOT NULL,
    nombre_completo             VARCHAR(150) NOT NULL,
    correo                      VARCHAR(150) NOT NULL,
    fecha_vencimiento_licencia  DATE         NOT NULL,
    activo                      BOOLEAN      NOT NULL,
    version                     BIGINT       NOT NULL
);

CREATE TABLE recordatorio_licencia (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_chofer             UUID        NOT NULL REFERENCES chofer_licencia(id_chofer),
    fecha_vencimiento     DATE        NOT NULL,
    dias_anticipacion     SMALLINT    NOT NULL CHECK (dias_anticipacion IN (30, 15, 3)),
    fecha_envio_programada DATE       NOT NULL,
    estado                VARCHAR(10) NOT NULL DEFAULT 'PENDIENTE'
        CHECK (estado IN ('PENDIENTE','ENVIADO','CANCELADO','FALLIDO')),
    intentos              SMALLINT    NOT NULL DEFAULT 0,
    enviado_en            TIMESTAMPTZ,
    creado_en             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_recordatorio UNIQUE (id_chofer, fecha_vencimiento, dias_anticipacion)
);
CREATE INDEX idx_recordatorio_pendiente
    ON recordatorio_licencia (fecha_envio_programada) WHERE estado = 'PENDIENTE';

-- Bitácora de correos (un registro por destinatario)
-- Para ENLACE_CONTRASENA el cuerpo NO se guarda (contiene un secreto): se escribe un texto fijo
CREATE TABLE notificacion (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo              VARCHAR(40)  NOT NULL,           -- COMPRA_BOLETOS, LICENCIA_POR_VENCER, ALQUILER_COTIZADO, ENLACE_CONTRASENA, CONTRASENA_CAMBIADA...
    correo_destino    VARCHAR(150) NOT NULL,
    id_usuario        UUID,
    asunto            VARCHAR(200) NOT NULL,
    cuerpo            TEXT         NOT NULL,
    estado            VARCHAR(10)  NOT NULL DEFAULT 'PENDIENTE'
        CHECK (estado IN ('PENDIENTE','ENVIADA','FALLIDA')),
    intentos          SMALLINT     NOT NULL DEFAULT 0,
    ultimo_error      TEXT,
    proximo_intento_en TIMESTAMPTZ,
    clave_dedup       VARCHAR(200) NOT NULL UNIQUE,    -- 'evt:{idEvento}:{correo}' o 'lic:{idRecordatorio}:{correo}'
    id_evento_origen  UUID,
    creado_en         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    enviada_en        TIMESTAMPTZ
);
CREATE INDEX idx_notificacion_pendiente
    ON notificacion (proximo_intento_en) WHERE estado = 'PENDIENTE';
