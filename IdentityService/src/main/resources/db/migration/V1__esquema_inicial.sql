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

-- Tablas propias

CREATE TABLE sucursal (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre          VARCHAR(100) NOT NULL UNIQUE,
    departamento    VARCHAR(50)  NOT NULL,
    direccion       VARCHAR(250) NOT NULL,
    telefono        VARCHAR(20),
    latitud         NUMERIC(9,6) NOT NULL CHECK (latitud  BETWEEN -90  AND 90),
    longitud        NUMERIC(9,6) NOT NULL CHECK (longitud BETWEEN -180 AND 180),
    activa          BOOLEAN      NOT NULL DEFAULT TRUE,
    version         BIGINT       NOT NULL DEFAULT 0,
    creado_en       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actualizado_en  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE usuario (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    correo           VARCHAR(150) NOT NULL,
    hash_contrasena  VARCHAR(100) NOT NULL,
    nombre_completo  VARCHAR(150) NOT NULL,
    rol              VARCHAR(20)  NOT NULL
        CHECK (rol IN ('ADMIN_SISTEMA','ADMIN_SUCURSAL','CAJERO','CHOFER','CLIENTE')),
    id_sucursal      UUID REFERENCES sucursal(id),
    activo           BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_por       UUID REFERENCES usuario(id),       -- quién lo creó (auditoría)
    version          BIGINT       NOT NULL DEFAULT 0,
    contrasena_cambiada_en TIMESTAMPTZ,                  -- auditoría del último cambio de contraseña
    creado_en        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actualizado_en   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- sucursal obligatoria para roles de sucursal; prohibida para los demás
    CONSTRAINT ck_usuario_sucursal CHECK (
        (rol IN ('ADMIN_SUCURSAL','CAJERO','CHOFER') AND id_sucursal IS NOT NULL)
     OR (rol IN ('ADMIN_SISTEMA','CLIENTE')          AND id_sucursal IS NULL)
    )
);
CREATE UNIQUE INDEX uq_usuario_correo ON usuario (lower(correo));
CREATE INDEX idx_usuario_sucursal_rol ON usuario (id_sucursal, rol) WHERE activo;

-- Enlaces de un solo uso para restablecer o activar una contraseña
CREATE TABLE token_contrasena (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_usuario     UUID         NOT NULL REFERENCES usuario(id),
    hash_token     CHAR(64)     NOT NULL UNIQUE,     -- SHA-256 (hex) del token: el token en claro NO se guarda
    proposito      VARCHAR(20)  NOT NULL
        CHECK (proposito IN ('RESTABLECIMIENTO','ACTIVACION')),
    expira_en      TIMESTAMPTZ  NOT NULL,            -- nunca se modifica después de crear el token
    usado_en       TIMESTAMPTZ,                      -- se llena al consumirlo
    invalidado_en  TIMESTAMPTZ,                      -- se llena cuando otro token más nuevo lo reemplaza
    ip_solicitud   VARCHAR(45),                      -- auditoría (IPv4 o IPv6)
    creado_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_token_vigencia CHECK (expira_en > creado_en),
    CONSTRAINT ck_token_estado   CHECK (usado_en IS NULL OR invalidado_en IS NULL)   -- no puede estar usado e invalidado a la vez
);
CREATE INDEX idx_token_usuario ON token_contrasena (id_usuario, creado_en DESC);
CREATE INDEX idx_token_expira  ON token_contrasena (expira_en);   -- para el job de limpieza

-- Un solo token activo por usuario y propósito (garantía de la base de datos, aun con solicitudes simultáneas)
CREATE UNIQUE INDEX uq_token_activo ON token_contrasena (id_usuario, proposito)
    WHERE usado_en IS NULL AND invalidado_en IS NULL;

-- Triggers

-- Los usuarios y sucursales no se eliminan
CREATE TRIGGER trg_usuario_sin_delete  BEFORE DELETE ON usuario
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_eliminacion();
CREATE TRIGGER trg_sucursal_sin_delete BEFORE DELETE ON sucursal
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_eliminacion();

-- Siempre debe existir al menos un administrador de sistema activo
CREATE OR REPLACE FUNCTION fn_proteger_ultimo_admin() RETURNS trigger AS $$
BEGIN
    IF OLD.rol = 'ADMIN_SISTEMA' AND OLD.activo
       AND (NOT NEW.activo OR NEW.rol <> 'ADMIN_SISTEMA') THEN   -- desactivarlo o cambiarle el rol
        PERFORM pg_advisory_xact_lock(1001);   -- serializa desactivaciones simultáneas
        IF NOT EXISTS (SELECT 1 FROM usuario
                        WHERE rol = 'ADMIN_SISTEMA' AND activo AND id <> OLD.id) THEN
            RAISE EXCEPTION 'Debe existir al menos un administrador de sistema activo';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_proteger_ultimo_admin BEFORE UPDATE ON usuario
    FOR EACH ROW EXECUTE FUNCTION fn_proteger_ultimo_admin();

-- Cada sucursal debe conservar al menos un administrador de sucursal activo
CREATE OR REPLACE FUNCTION fn_proteger_ultimo_admin_sucursal() RETURNS trigger AS $$
BEGIN
    IF OLD.rol = 'ADMIN_SUCURSAL' AND OLD.activo
       AND (NOT NEW.activo OR NEW.rol <> 'ADMIN_SUCURSAL'
            OR NEW.id_sucursal IS DISTINCT FROM OLD.id_sucursal) THEN
        PERFORM pg_advisory_xact_lock(hashtext(OLD.id_sucursal::text));   -- un bloqueo por sucursal
        IF NOT EXISTS (SELECT 1 FROM usuario
                        WHERE rol = 'ADMIN_SUCURSAL' AND activo
                          AND id_sucursal = OLD.id_sucursal AND id <> OLD.id) THEN
            RAISE EXCEPTION 'La sucursal debe conservar al menos un administrador de sucursal activo';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_proteger_ultimo_admin_sucursal BEFORE UPDATE ON usuario
    FOR EACH ROW EXECUTE FUNCTION fn_proteger_ultimo_admin_sucursal();

-- Los administradores de sucursal solo los crea un administrador de sistema activo
CREATE OR REPLACE FUNCTION fn_validar_creador_admin_sucursal() RETURNS trigger AS $$
BEGIN
    IF NEW.rol = 'ADMIN_SUCURSAL' AND NOT EXISTS (
            SELECT 1 FROM usuario
             WHERE id = NEW.creado_por AND rol = 'ADMIN_SISTEMA' AND activo) THEN
        RAISE EXCEPTION 'Solo un administrador de sistema activo puede crear administradores de sucursal';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_admin_sucursal_creador BEFORE INSERT OR UPDATE OF rol ON usuario
    FOR EACH ROW EXECUTE FUNCTION fn_validar_creador_admin_sucursal();
