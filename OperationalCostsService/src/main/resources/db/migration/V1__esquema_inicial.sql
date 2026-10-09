-- bd_costos — Costos Operativos (modelo-datos-postgresql-v4.md)

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

-- Tablas, índices y triggers

CREATE TABLE sucursal_ref (
    id        UUID PRIMARY KEY,
    nombre    VARCHAR(100) NOT NULL,
    activa    BOOLEAN      NOT NULL,
    version   BIGINT       NOT NULL
);

CREATE TABLE bus_ref (
    id           UUID PRIMARY KEY,
    id_sucursal  UUID        NOT NULL REFERENCES sucursal_ref(id),
    placa        VARCHAR(15) NOT NULL,
    activo       BOOLEAN     NOT NULL,
    version      BIGINT      NOT NULL
);

-- Historial de tarifas: el cambio de monto NO altera los registros ya calculados
CREATE TABLE configuracion_depreciacion (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    monto_por_km   NUMERIC(10,4) NOT NULL CHECK (monto_por_km >= 0),
    vigente_desde  DATE          NOT NULL UNIQUE,
    creado_por     UUID          NOT NULL,                  -- administrador de sistema
    creado_en      TIMESTAMPTZ   NOT NULL DEFAULT now()
);
-- Tarifa aplicable a una fecha:
--   SELECT monto_por_km FROM configuracion_depreciacion
--    WHERE vigente_desde <= :fecha ORDER BY vigente_desde DESC LIMIT 1;

-- Dato inicial obligatorio: sin una tarifa vigente, el primer LlegadaRegistrada no podría calcular la depreciación
INSERT INTO configuracion_depreciacion (monto_por_km, vigente_desde, creado_por)
VALUES (2.0000, DATE '2026-01-01', 'b0000000-0000-0000-0000-000000000001');

CREATE TABLE categoria_gasto (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre       VARCHAR(80)  NOT NULL,
    descripcion  VARCHAR(250),
    activa       BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_categoria_nombre ON categoria_gasto (lower(nombre));

INSERT INTO categoria_gasto (nombre) VALUES
    ('Llantas'), ('Motor'), ('Frenos'), ('Suspensión'),
    ('Cambio de aceite'), ('Eléctrico'), ('Carrocería'), ('Otros');

CREATE TABLE gasto_taller (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_bus          UUID          NOT NULL REFERENCES bus_ref(id),
    id_sucursal     UUID          NOT NULL REFERENCES sucursal_ref(id),
    fecha_servicio  DATE          NOT NULL,                 -- elegida por el usuario
    total           NUMERIC(12,2) NOT NULL CHECK (total >= 0),
    observaciones   VARCHAR(500),
    registrado_por  UUID          NOT NULL,
    creado_en       TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_gasto_taller_bus ON gasto_taller (id_bus, fecha_servicio);
CREATE INDEX idx_gasto_taller_sucursal ON gasto_taller (id_sucursal, fecha_servicio);

CREATE TABLE gasto_taller_linea (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_gasto_taller  UUID          NOT NULL REFERENCES gasto_taller(id) ON DELETE RESTRICT,
    id_categoria     UUID          NOT NULL REFERENCES categoria_gasto(id) ON DELETE RESTRICT,  -- obligatoria
    descripcion      VARCHAR(250)  NOT NULL,
    tipo             VARCHAR(15)   NOT NULL CHECK (tipo IN ('MANO_DE_OBRA','REPUESTO')),
    monto            NUMERIC(12,2) NOT NULL CHECK (monto >= 0)
);
CREATE INDEX idx_taller_linea_gasto     ON gasto_taller_linea (id_gasto_taller);
CREATE INDEX idx_taller_linea_categoria ON gasto_taller_linea (id_categoria);

-- Generados al consumir LlegadaRegistrada. UNIQUE(id_viaje) = idempotencia
CREATE TABLE gasto_combustible (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_viaje     UUID          NOT NULL UNIQUE,
    id_bus       UUID          NOT NULL REFERENCES bus_ref(id),
    id_sucursal  UUID          NOT NULL REFERENCES sucursal_ref(id),
    monto        NUMERIC(12,2) NOT NULL CHECK (monto >= 0),
    fecha_gasto  DATE          NOT NULL,
    creado_en    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_combustible_bus ON gasto_combustible (id_bus, fecha_gasto);

CREATE TABLE registro_depreciacion (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_viaje      UUID          NOT NULL UNIQUE,
    id_bus        UUID          NOT NULL REFERENCES bus_ref(id),
    id_sucursal   UUID          NOT NULL REFERENCES sucursal_ref(id),
    distancia_km  NUMERIC(10,1) NOT NULL CHECK (distancia_km >= 0),
    monto_por_km  NUMERIC(10,4) NOT NULL,                   -- tarifa usada (instantánea)
    monto         NUMERIC(12,2) NOT NULL CHECK (monto >= 0),
    fecha_calculo DATE          NOT NULL,
    creado_en     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_depreciacion_bus ON registro_depreciacion (id_bus, fecha_calculo);

-- El salario del chofer cuenta como gasto (decisión de diseño). Se genera al consumir LlegadaRegistrada
CREATE TABLE pago_chofer (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_viaje       UUID          NOT NULL UNIQUE,
    id_chofer      UUID          NOT NULL,
    id_sucursal    UUID          NOT NULL REFERENCES sucursal_ref(id),
    salario_base   NUMERIC(10,2) NOT NULL,
    multiplicador  NUMERIC(4,2)  NOT NULL CHECK (multiplicador IN (1.00, 1.15)),   -- 1.15 en alquiler privado
    monto          NUMERIC(12,2) NOT NULL,
    fecha_pago     DATE          NOT NULL,
    creado_en      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Gasto de taller: el total del encabezado debe ser igual a la suma de sus líneas
-- (se valida al final de la transacción, cuando ya se insertaron todas las líneas)
CREATE OR REPLACE FUNCTION fn_validar_total_taller() RETURNS trigger AS $$
DECLARE
    v_total NUMERIC(12,2);
    v_suma  NUMERIC(12,2);
BEGIN
    SELECT total INTO v_total FROM gasto_taller WHERE id = NEW.id_gasto_taller;
    SELECT COALESCE(SUM(monto), 0) INTO v_suma
      FROM gasto_taller_linea WHERE id_gasto_taller = NEW.id_gasto_taller;
    IF v_total <> v_suma THEN
        RAISE EXCEPTION 'El total del gasto de taller (%) no coincide con la suma de sus líneas (%)', v_total, v_suma;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_taller_total AFTER INSERT ON gasto_taller_linea
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION fn_validar_total_taller();

-- Gastos de taller y salarios: una vez registrados no se modifican ni se eliminan
CREATE TRIGGER trg_taller_inmutable       BEFORE UPDATE OR DELETE ON gasto_taller
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_modificacion();
CREATE TRIGGER trg_taller_linea_inmutable BEFORE UPDATE OR DELETE ON gasto_taller_linea
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_modificacion();
CREATE TRIGGER trg_pago_chofer_inmutable  BEFORE UPDATE OR DELETE ON pago_chofer
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_modificacion();

-- Registros financieros inmutables
CREATE TRIGGER trg_combustible_inmutable  BEFORE UPDATE OR DELETE ON gasto_combustible
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_modificacion();
CREATE TRIGGER trg_depreciacion_inmutable BEFORE UPDATE OR DELETE ON registro_depreciacion
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_modificacion();
