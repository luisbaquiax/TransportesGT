-- bd_reportes — Reportes (proyecciones de lectura) (modelo-datos-postgresql-v4.md)

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

CREATE TABLE rep_sucursal (
    id            UUID PRIMARY KEY,
    nombre        VARCHAR(100) NOT NULL,
    departamento  VARCHAR(50),
    latitud       NUMERIC(9,6),
    longitud      NUMERIC(9,6),
    activa        BOOLEAN      NOT NULL,
    version       BIGINT       NOT NULL
);

CREATE TABLE rep_bus (
    id                  UUID PRIMARY KEY,
    id_sucursal         UUID         NOT NULL,
    placa               VARCHAR(15)  NOT NULL,
    marca               VARCHAR(50),
    modelo              VARCHAR(50),
    capacidad_pasajeros SMALLINT,
    estado_operativo    VARCHAR(20),
    activo              BOOLEAN      NOT NULL,
    kilometraje_actual  NUMERIC(10,1) NOT NULL DEFAULT 0,   -- se actualiza con LlegadaRegistrada
    version             BIGINT       NOT NULL
);
CREATE INDEX idx_rep_bus_sucursal ON rep_bus (id_sucursal, estado_operativo);

CREATE TABLE rep_chofer (
    id                          UUID PRIMARY KEY,
    id_sucursal                 UUID         NOT NULL,
    numero_licencia             VARCHAR(30),
    nombre_completo             VARCHAR(150) NOT NULL,
    tipo_licencia               VARCHAR(2),
    fecha_vencimiento_licencia  DATE,
    activo                      BOOLEAN      NOT NULL,
    version                     BIGINT       NOT NULL
);

CREATE TABLE rep_ruta (
    id                   UUID PRIMARY KEY,
    id_sucursal_origen   UUID          NOT NULL,
    id_sucursal_destino  UUID          NOT NULL,
    distancia_km         NUMERIC(8,2),
    precio_boleto        NUMERIC(10,2),
    eliminada            BOOLEAN       NOT NULL DEFAULT FALSE,
    version              BIGINT        NOT NULL
);
CREATE INDEX idx_rep_ruta_origen ON rep_ruta (id_sucursal_origen) WHERE NOT eliminada;   -- mapa de rutas

CREATE TABLE rep_viaje (
    id                           UUID PRIMARY KEY,
    tipo_viaje                   VARCHAR(20)  NOT NULL,
    estado                       VARCHAR(25)  NOT NULL,
    id_ruta                      UUID,
    id_alquiler                  UUID,
    id_sucursal_origen           UUID         NOT NULL,
    nombre_origen                VARCHAR(150),
    nombre_destino               VARCHAR(150),
    id_bus                       UUID,
    id_chofer                    UUID,
    fecha_hora_salida            TIMESTAMPTZ,
    fecha_hora_llegada_estimada  TIMESTAMPTZ,
    fecha_hora_salida_real       TIMESTAMPTZ,
    fecha_hora_llegada_real      TIMESTAMPTZ,
    kilometraje_inicial          NUMERIC(10,1),
    kilometraje_final            NUMERIC(10,1),
    distancia_recorrida_km       NUMERIC(10,1),
    eliminado                    BOOLEAN      NOT NULL DEFAULT FALSE,
    version                      BIGINT       NOT NULL
);
CREATE INDEX idx_rep_viaje_bus      ON rep_viaje (id_bus) WHERE NOT eliminado;
CREATE INDEX idx_rep_viaje_chofer   ON rep_viaje (id_chofer) WHERE NOT eliminado;
CREATE INDEX idx_rep_viaje_alquiler ON rep_viaje (id_alquiler) WHERE id_alquiler IS NOT NULL;

-- Ingresos: boletos y alquileres en una sola tabla
CREATE TABLE rep_ingreso (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo_ingreso      VARCHAR(15)   NOT NULL CHECK (tipo_ingreso IN ('VENTA_BOLETOS','ALQUILER')),
    id_referencia     UUID          NOT NULL,                  -- idCompra / idAlquiler
    id_sucursal       UUID          NOT NULL,
    fecha_ingreso     DATE          NOT NULL,                  -- fecha de pago elegida por el usuario
    monto             NUMERIC(12,2) NOT NULL,
    -- Venta de boletos
    id_viaje          UUID,
    id_ruta           UUID,
    cantidad_boletos  SMALLINT,
    canal             VARCHAR(10),
    -- Cliente (alquileres y boletos)
    nit_cliente       VARCHAR(20),
    nombre_cliente    VARCHAR(150),
    -- Alquiler
    origen            VARCHAR(150),
    destino           VARCHAR(150),
    fecha_salida      TIMESTAMPTZ,
    fecha_retorno     TIMESTAMPTZ,
    -- Reversión (BoletosAnulados)
    anulado           BOOLEAN       NOT NULL DEFAULT FALSE,
    anulado_en        TIMESTAMPTZ,
    creado_en         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_rep_ingreso UNIQUE (tipo_ingreso, id_referencia)
);
CREATE INDEX idx_rep_ingreso_sucursal_fecha ON rep_ingreso (id_sucursal, fecha_ingreso) WHERE NOT anulado;
CREATE INDEX idx_rep_ingreso_ruta           ON rep_ingreso (id_ruta, fecha_ingreso)     WHERE NOT anulado;
CREATE INDEX idx_rep_ingreso_viaje          ON rep_ingreso (id_viaje)                   WHERE NOT anulado;

-- Gastos: combustible, taller (una fila por línea), depreciación y salario
CREATE TABLE rep_gasto (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo_gasto       VARCHAR(20)   NOT NULL
        CHECK (tipo_gasto IN ('COMBUSTIBLE','TALLER','DEPRECIACION','SALARIO_CHOFER')),
    id_referencia    UUID          NOT NULL,             -- idGasto / idLinea / idDepreciacion / idPago
    id_sucursal      UUID          NOT NULL,
    id_bus           UUID,
    id_viaje         UUID,
    fecha_gasto      DATE          NOT NULL,
    monto            NUMERIC(12,2) NOT NULL,
    -- Solo taller
    id_categoria     UUID,
    nombre_categoria VARCHAR(80),
    tipo_linea       VARCHAR(15)   CHECK (tipo_linea IN ('MANO_DE_OBRA','REPUESTO')),
    descripcion      VARCHAR(250),
    -- Solo depreciación
    distancia_km     NUMERIC(10,1),
    monto_por_km     NUMERIC(10,4),
    creado_en        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_rep_gasto UNIQUE (tipo_gasto, id_referencia)
);
CREATE INDEX idx_rep_gasto_sucursal_fecha ON rep_gasto (id_sucursal, fecha_gasto);
CREATE INDEX idx_rep_gasto_bus            ON rep_gasto (id_bus, tipo_gasto);
CREATE INDEX idx_rep_gasto_categoria      ON rep_gasto (id_categoria, fecha_gasto) WHERE tipo_gasto = 'TALLER';

CREATE TABLE rep_tarifa_depreciacion (
    vigente_desde  DATE PRIMARY KEY,
    monto_por_km   NUMERIC(10,4) NOT NULL
);

-- Resumen diario por sucursal: base del reporte de ganancias
-- (la aplicación filtra por rango de fechas y suma)
CREATE VIEW v_resumen_diario_sucursal AS
SELECT COALESCE(i.id_sucursal, g.id_sucursal) AS id_sucursal,
       COALESCE(i.fecha, g.fecha)             AS fecha,
       COALESCE(i.ingresos, 0)                AS ingresos,
       COALESCE(g.gastos, 0)                  AS gastos,
       COALESCE(i.ingresos, 0) - COALESCE(g.gastos, 0) AS ganancia
FROM (SELECT id_sucursal, fecha_ingreso AS fecha, SUM(monto) AS ingresos
        FROM rep_ingreso WHERE NOT anulado GROUP BY 1, 2) i
FULL JOIN
     (SELECT id_sucursal, fecha_gasto AS fecha, SUM(monto) AS gastos
        FROM rep_gasto GROUP BY 1, 2) g
  ON g.id_sucursal = i.id_sucursal AND g.fecha = i.fecha;
