-- bd_flota — Flota (modelo-datos-postgresql-v4.md)

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

-- Funciones auxiliares (solo las que usa esta base)

-- Bloquea solo DELETE (los registros se desactivan, no se eliminan)
CREATE OR REPLACE FUNCTION fn_bloquear_eliminacion() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Los registros de % no se eliminan, solo se desactivan', TG_TABLE_NAME
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;

-- Tablas, índices y triggers

CREATE TABLE sucursal_ref (
    id        UUID PRIMARY KEY,
    nombre    VARCHAR(100) NOT NULL,
    activa    BOOLEAN      NOT NULL,
    version   BIGINT       NOT NULL
);

CREATE TABLE bus (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_sucursal         UUID         NOT NULL REFERENCES sucursal_ref(id),
    placa               VARCHAR(15)  NOT NULL,
    marca               VARCHAR(50)  NOT NULL,
    modelo              VARCHAR(50)  NOT NULL,
    anio_fabricacion    SMALLINT     NOT NULL CHECK (anio_fabricacion BETWEEN 1980 AND 2100),
    capacidad_pasajeros SMALLINT     NOT NULL CHECK (capacidad_pasajeros > 0),
    estado_operativo    VARCHAR(20)  NOT NULL DEFAULT 'DISPONIBLE'
        CHECK (estado_operativo IN ('DISPONIBLE','EN_TRANSITO','EN_TALLER','FUERA_DE_SERVICIO')),
    kilometraje_actual  NUMERIC(10,1) NOT NULL DEFAULT 0 CHECK (kilometraje_actual >= 0),
    foto_url            VARCHAR(500),
    activo              BOOLEAN      NOT NULL DEFAULT TRUE,
    version             BIGINT       NOT NULL DEFAULT 0,
    creado_en           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actualizado_en      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_bus_placa ON bus (upper(placa));
CREATE INDEX idx_bus_sucursal ON bus (id_sucursal, activo);

CREATE TABLE chofer (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_usuario                  UUID UNIQUE,            -- usuario de Identidad (rol CHOFER), si tiene acceso
    id_sucursal                 UUID         NOT NULL REFERENCES sucursal_ref(id),
    nombre_completo             VARCHAR(150) NOT NULL,
    correo                      VARCHAR(150) NOT NULL,
    telefono                    VARCHAR(20)  NOT NULL,
    numero_licencia             VARCHAR(30)  NOT NULL UNIQUE,
    tipo_licencia               VARCHAR(2)   NOT NULL CHECK (tipo_licencia IN ('A','B','C','M','E')),
    fecha_vencimiento_licencia  DATE         NOT NULL,
    salario_base_viaje          NUMERIC(10,2) NOT NULL CHECK (salario_base_viaje >= 0),
    foto_url                    VARCHAR(500),
    activo                      BOOLEAN      NOT NULL DEFAULT TRUE,
    version                     BIGINT       NOT NULL DEFAULT 0,
    creado_en                   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actualizado_en              TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_chofer_sucursal ON chofer (id_sucursal, activo);
CREATE INDEX idx_chofer_vencimiento ON chofer (fecha_vencimiento_licencia) WHERE activo;

CREATE TRIGGER trg_bus_sin_delete    BEFORE DELETE ON bus
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_eliminacion();
CREATE TRIGGER trg_chofer_sin_delete BEFORE DELETE ON chofer
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_eliminacion();
