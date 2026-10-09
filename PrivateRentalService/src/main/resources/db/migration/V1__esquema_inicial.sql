-- bd_alquileres — Alquileres Privados (modelo-datos-postgresql-v4.md)

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

-- Valida transiciones de estado. Se usa como:
--   EXECUTE FUNCTION fn_validar_transicion_estado('estado', 'A>B', 'B>C', ...)
-- Si el estado no cambia, no valida nada.
CREATE OR REPLACE FUNCTION fn_validar_transicion_estado() RETURNS trigger AS $$
DECLARE
    v_desde TEXT := to_jsonb(OLD) ->> TG_ARGV[0];
    v_hasta TEXT := to_jsonb(NEW) ->> TG_ARGV[0];
BEGIN
    IF v_desde IS NOT DISTINCT FROM v_hasta THEN
        RETURN NEW;
    END IF;
    FOR i IN 1 .. TG_NARGS - 1 LOOP
        IF TG_ARGV[i] = v_desde || '>' || v_hasta THEN
            RETURN NEW;
        END IF;
    END LOOP;
    RAISE EXCEPTION 'Transición de estado no permitida en %: % -> %', TG_TABLE_NAME, v_desde, v_hasta
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

CREATE TABLE alquiler (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    canal                VARCHAR(10)   NOT NULL CHECK (canal IN ('EN_LINEA','MOSTRADOR')),
    estado               VARCHAR(15)   NOT NULL DEFAULT 'SOLICITADO'
        CHECK (estado IN ('SOLICITADO','COTIZADO','CONFIRMADO','FINALIZADO','CANCELADO')),
    id_usuario_cliente   UUID,                                -- null si se solicitó en mostrador sin cuenta
    nombre_cliente       VARCHAR(150)  NOT NULL,
    nit_cliente          VARCHAR(20)   NOT NULL,                -- normalizado; 'CF' en mostrador sin NIT
    id_sucursal_origen   UUID          NOT NULL REFERENCES sucursal_ref(id),
    origen               VARCHAR(150)  NOT NULL,
    destino              VARCHAR(150)  NOT NULL,
    fecha_hora_salida    TIMESTAMPTZ   NOT NULL,
    fecha_hora_retorno   TIMESTAMPTZ,
    fecha_hora_llegada_estimada TIMESTAMPTZ,              -- la fija el administrador al cotizar
    ida_y_vuelta         BOOLEAN       NOT NULL DEFAULT FALSE,
    numero_pasajeros     SMALLINT      NOT NULL CHECK (numero_pasajeros > 0),
    distancia_estimada_km NUMERIC(8,2)  NOT NULL CHECK (distancia_estimada_km > 0),   -- la indica el solicitante; el administrador puede corregirla al cotizar
    precio_estimado      NUMERIC(12,2) NOT NULL CHECK (precio_estimado >= 0),
    precio_final         NUMERIC(12,2) CHECK (precio_final >= 0),
    id_pago              UUID,                                -- movimiento de cartera
    fecha_pago           DATE,                                -- elegida por el usuario
    id_viaje             UUID,                                -- se llena al consumir ViajeProgramado
    id_bus               UUID,
    placa_bus            VARCHAR(15),
    id_chofer            UUID,
    nombre_chofer        VARCHAR(150),
    motivo_cancelacion   VARCHAR(30),
    cancelado_en         TIMESTAMPTZ,
    version              BIGINT        NOT NULL DEFAULT 0,
    creado_en            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizado_en       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_alquiler_retorno CHECK (
        (ida_y_vuelta AND fecha_hora_retorno IS NOT NULL AND fecha_hora_retorno > fecha_hora_salida)
     OR (NOT ida_y_vuelta AND fecha_hora_retorno IS NULL)
    ),
    -- pagado en cartera (id_pago) o, en mostrador, pagado en efectivo (solo fecha_pago)
    CONSTRAINT ck_alquiler_confirmado CHECK (
        estado NOT IN ('CONFIRMADO','FINALIZADO')
        OR (precio_final IS NOT NULL AND fecha_pago IS NOT NULL
            AND fecha_hora_llegada_estimada IS NOT NULL
            AND (canal = 'MOSTRADOR' OR id_pago IS NOT NULL))
    ),
    -- ida y vuelta = un solo viaje: la llegada estimada es el regreso al origen
    CONSTRAINT ck_alquiler_llegada CHECK (
        fecha_hora_llegada_estimada IS NULL
        OR (fecha_hora_llegada_estimada > fecha_hora_salida
            AND (NOT ida_y_vuelta OR fecha_hora_llegada_estimada >= fecha_hora_retorno))
    )
);
CREATE UNIQUE INDEX uq_alquiler_viaje ON alquiler (id_viaje) WHERE id_viaje IS NOT NULL;
CREATE INDEX idx_alquiler_nit       ON alquiler (nit_cliente);          -- historial por NIT
CREATE INDEX idx_alquiler_usuario   ON alquiler (id_usuario_cliente);
CREATE INDEX idx_alquiler_sucursal  ON alquiler (id_sucursal_origen, estado);

-- Historial de precios: estimado por el sistema y ajustes del administrador
CREATE TABLE cotizacion_alquiler (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_alquiler  UUID          NOT NULL REFERENCES alquiler(id),
    tipo         VARCHAR(15)   NOT NULL CHECK (tipo IN ('ESTIMADA','AJUSTADA_ADMIN')),
    precio       NUMERIC(12,2) NOT NULL CHECK (precio >= 0),
    id_usuario   UUID,                                                  -- null cuando es ESTIMADA por el sistema
    comentario   VARCHAR(250),
    creado_en    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_cotizacion_alquiler ON cotizacion_alquiler (id_alquiler, creado_en);

-- Tarifas para el precio estimado. Las configura el administrador de sistema y se conserva el historial
CREATE TABLE tarifa_alquiler (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    precio_por_km   NUMERIC(10,2) NOT NULL CHECK (precio_por_km  >= 0),
    precio_por_dia  NUMERIC(10,2) NOT NULL CHECK (precio_por_dia >= 0),
    vigente_desde   DATE          NOT NULL UNIQUE,
    creado_por      UUID          NOT NULL,                  -- administrador de sistema
    creado_en       TIMESTAMPTZ   NOT NULL DEFAULT now()
);
-- Dato inicial obligatorio (valores de ejemplo, ajustables): sin una tarifa no se puede estimar ningún alquiler
INSERT INTO tarifa_alquiler (precio_por_km, precio_por_dia, vigente_desde, creado_por)
VALUES (10.00, 500.00, DATE '2026-01-01', 'b0000000-0000-0000-0000-000000000001');

-- Perfil comercial del cliente (alimentada por PerfilClienteActualizado)
CREATE TABLE cliente_ref (
    id_usuario       UUID PRIMARY KEY,
    nit              VARCHAR(20),                  -- normalizado; null mientras el perfil no tenga NIT
    nombre_completo  VARCHAR(150) NOT NULL,
    activo           BOOLEAN      NOT NULL,
    version          BIGINT       NOT NULL
);

-- Ciclo de vida del alquiler (COTIZADO puede repetirse: el administrador puede reajustar el precio)
CREATE TRIGGER trg_alquiler_transicion BEFORE UPDATE OF estado ON alquiler
    FOR EACH ROW EXECUTE FUNCTION fn_validar_transicion_estado(
        'estado',
        'SOLICITADO>COTIZADO', 'SOLICITADO>CANCELADO',
        'COTIZADO>CONFIRMADO', 'COTIZADO>CANCELADO',
        'CONFIRMADO>FINALIZADO', 'CONFIRMADO>CANCELADO');
