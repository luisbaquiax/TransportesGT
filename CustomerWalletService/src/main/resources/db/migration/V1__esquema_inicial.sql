-- bd_clientes — Clientes y Cartera (modelo-datos-postgresql-v4.md §5)

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

-- Bloquea UPDATE y DELETE (registros inmutables)
CREATE OR REPLACE FUNCTION fn_bloquear_modificacion() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Los registros de % son inmutables: no se pueden modificar ni eliminar', TG_TABLE_NAME
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;

-- Tablas propias

CREATE TABLE cliente (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_usuario       UUID         NOT NULL UNIQUE,         -- usuario de Identidad (cualquier rol)
    nombre_completo  VARCHAR(150) NOT NULL,
    correo           VARCHAR(150) NOT NULL,
    nit              VARCHAR(20)                           -- se completa en el perfil; normalizado (mayúsculas, sin guiones)
        CHECK (nit ~ '^[0-9A-Z]{2,20}$' AND nit <> 'CF'),     -- 'CF' (consumidor final) no es un NIT de cliente
    dpi              CHAR(13)     CHECK (dpi ~ '^[0-9]{13}$'),
    telefono         VARCHAR(20),
    direccion        VARCHAR(250),
    activo           BOOLEAN      NOT NULL DEFAULT TRUE,
    version_usuario  BIGINT       NOT NULL DEFAULT 0,      -- versionAgregado del último UsuarioCreado/Actualizado aplicado
    version          BIGINT       NOT NULL DEFAULT 0,
    creado_en        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actualizado_en   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_cliente_nit ON cliente (nit) WHERE nit IS NOT NULL;
CREATE UNIQUE INDEX uq_cliente_dpi ON cliente (dpi) WHERE dpi IS NOT NULL;

CREATE TABLE cartera (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_cliente      UUID          NOT NULL UNIQUE REFERENCES cliente(id),
    saldo           NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (saldo >= 0),
    version         BIGINT        NOT NULL DEFAULT 0,      -- bloqueo optimista contra pagos simultáneos
    actualizado_en  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE movimiento_cartera (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),   -- = idPago / idMovimiento
    id_cartera          UUID          NOT NULL REFERENCES cartera(id),
    tipo                VARCHAR(10)   NOT NULL CHECK (tipo IN ('RECARGA','PAGO','REEMBOLSO')),
    monto               NUMERIC(12,2) NOT NULL CHECK (monto > 0),
    saldo_posterior     NUMERIC(12,2) NOT NULL CHECK (saldo_posterior >= 0),
    tipo_referencia     VARCHAR(10)   CHECK (tipo_referencia IN ('BOLETOS','ALQUILER')),
    id_referencia       UUID,                               -- idCompra / idAlquiler
    clave_idempotencia  VARCHAR(100)  UNIQUE,               -- encabezado Clave-Idempotencia
    fecha_movimiento    DATE          NOT NULL,             -- elegida por el usuario
    descripcion         VARCHAR(250),
    creado_en           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_mov_referencia CHECK (
        (tipo = 'RECARGA' AND tipo_referencia IS NULL AND id_referencia IS NULL)
     OR (tipo IN ('PAGO','REEMBOLSO') AND tipo_referencia IS NOT NULL AND id_referencia IS NOT NULL)
    )
);
-- Un solo PAGO y un solo REEMBOLSO por referencia: evita cobros y reembolsos duplicados
CREATE UNIQUE INDEX uq_movimiento_referencia
    ON movimiento_cartera (tipo, tipo_referencia, id_referencia)
    WHERE tipo IN ('PAGO','REEMBOLSO');
CREATE INDEX idx_movimiento_cartera ON movimiento_cartera (id_cartera, fecha_movimiento DESC);

-- Triggers

-- El libro de movimientos es inmutable
CREATE TRIGGER trg_movimiento_inmutable BEFORE UPDATE OR DELETE ON movimiento_cartera
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_modificacion();
