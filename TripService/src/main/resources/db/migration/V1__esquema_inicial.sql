-- bd_viajes — Rutas y Viajes (modelo-datos-postgresql-v4.md)

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

CREATE EXTENSION IF NOT EXISTS btree_gist;   -- para evitar traslapes de bus y chofer

CREATE TABLE sucursal_ref (
    id        UUID PRIMARY KEY,
    nombre    VARCHAR(100) NOT NULL,
    activa    BOOLEAN      NOT NULL,
    version   BIGINT       NOT NULL
);

CREATE TABLE bus_ref (
    id                  UUID PRIMARY KEY,
    id_sucursal         UUID        NOT NULL,
    placa               VARCHAR(15) NOT NULL,
    capacidad_pasajeros SMALLINT    NOT NULL,
    estado_operativo    VARCHAR(20) NOT NULL DEFAULT 'DISPONIBLE',   -- viene en BusActualizado
    activo              BOOLEAN     NOT NULL,
    version             BIGINT      NOT NULL
);

CREATE TABLE chofer_ref (
    id                  UUID PRIMARY KEY,
    id_usuario          UUID UNIQUE,                     -- usuario con rol CHOFER (solo registra salida y llegada)
    id_sucursal         UUID         NOT NULL,
    nombre_completo     VARCHAR(150) NOT NULL,
    salario_base_viaje  NUMERIC(10,2) NOT NULL,
    fecha_vencimiento_licencia DATE  NOT NULL,           -- para no asignar choferes con licencia vencida
    activo              BOOLEAN      NOT NULL,
    version             BIGINT       NOT NULL
);

CREATE TABLE ruta (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_sucursal_origen    UUID          NOT NULL REFERENCES sucursal_ref(id),
    id_sucursal_destino   UUID          NOT NULL REFERENCES sucursal_ref(id),
    distancia_km          NUMERIC(8,2)  NOT NULL CHECK (distancia_km > 0),
    precio_boleto         NUMERIC(10,2) NOT NULL CHECK (precio_boleto >= 0),
    creado_por            UUID          NOT NULL,     -- administrador de la sucursal de origen
    version               BIGINT        NOT NULL DEFAULT 0,
    creado_en             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizado_en        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_ruta_distinta CHECK (id_sucursal_origen <> id_sucursal_destino),
    CONSTRAINT uq_ruta_origen_destino UNIQUE (id_sucursal_origen, id_sucursal_destino)
);

CREATE TABLE viaje (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo_viaje                  VARCHAR(20) NOT NULL
        CHECK (tipo_viaje IN ('REGULAR','ALQUILER_PRIVADO')),
    estado                      VARCHAR(25) NOT NULL
        CHECK (estado IN ('PROGRAMADO','PENDIENTE_ASIGNACION','EN_TRANSITO','FINALIZADO')),
    id_ruta                     UUID REFERENCES ruta(id) ON DELETE RESTRICT,   -- solo REGULAR
    id_alquiler                 UUID UNIQUE,                                   -- solo ALQUILER_PRIVADO
    id_sucursal_origen          UUID        NOT NULL REFERENCES sucursal_ref(id),
    nombre_origen               VARCHAR(150) NOT NULL,
    nombre_destino              VARCHAR(150) NOT NULL,
    id_bus                      UUID REFERENCES bus_ref(id),
    id_chofer                   UUID REFERENCES chofer_ref(id),
    fecha_hora_salida           TIMESTAMPTZ NOT NULL,
    fecha_hora_llegada_estimada TIMESTAMPTZ NOT NULL,
    precio_boleto               NUMERIC(10,2),                                 -- instantánea del precio de la ruta
    numero_pasajeros            SMALLINT CHECK (numero_pasajeros > 0),         -- solo ALQUILER_PRIVADO (viene en AlquilerConfirmado)
    version                     BIGINT      NOT NULL DEFAULT 0,
    creado_por                  UUID        NOT NULL,
    creado_en                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_viaje_fechas CHECK (fecha_hora_llegada_estimada > fecha_hora_salida),
    -- REGULAR exige ruta y precio; ALQUILER_PRIVADO exige alquiler y no tiene ruta
    CONSTRAINT ck_viaje_tipo CHECK (
        (tipo_viaje = 'REGULAR'          AND id_ruta IS NOT NULL AND id_alquiler IS NULL AND precio_boleto IS NOT NULL
                                         AND numero_pasajeros IS NULL)
     OR (tipo_viaje = 'ALQUILER_PRIVADO' AND id_ruta IS NULL     AND id_alquiler IS NOT NULL
                                         AND numero_pasajeros IS NOT NULL)
    ),
    -- sin bus ni chofer solo mientras esté pendiente de asignación
    CONSTRAINT ck_viaje_recursos CHECK (
        estado = 'PENDIENTE_ASIGNACION' OR (id_bus IS NOT NULL AND id_chofer IS NOT NULL)
    ),
    -- un bus o un chofer no pueden estar en dos viajes que se traslapan
    CONSTRAINT ex_viaje_bus EXCLUDE USING gist (
        id_bus WITH =,
        tstzrange(fecha_hora_salida, fecha_hora_llegada_estimada, '[)') WITH &&
    ) WHERE (id_bus IS NOT NULL),
    CONSTRAINT ex_viaje_chofer EXCLUDE USING gist (
        id_chofer WITH =,
        tstzrange(fecha_hora_salida, fecha_hora_llegada_estimada, '[)') WITH &&
    ) WHERE (id_chofer IS NOT NULL)
);
CREATE INDEX idx_viaje_ruta        ON viaje (id_ruta, fecha_hora_salida);
CREATE INDEX idx_viaje_bus_estado  ON viaje (id_bus, estado);     -- consulta de Flota: ¿bus con viajes activos?
CREATE INDEX idx_viaje_sucursal    ON viaje (id_sucursal_origen, fecha_hora_salida);

-- El tipo de viaje no se puede modificar una vez definido
CREATE OR REPLACE FUNCTION fn_viaje_tipo_inmutable() RETURNS trigger AS $$
BEGIN
    IF NEW.tipo_viaje <> OLD.tipo_viaje THEN
        RAISE EXCEPTION 'El tipo de viaje no se puede modificar';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_viaje_tipo_inmutable BEFORE UPDATE ON viaje
    FOR EACH ROW EXECUTE FUNCTION fn_viaje_tipo_inmutable();

-- Validación de recursos al asignar bus y chofer (se apoya en las réplicas locales).
-- Solo valida cuando el recurso se asigna o cambia, para no bloquear, por ejemplo,
-- que un viaje en tránsito pase a FINALIZADO si el bus se desactivó mientras tanto.
CREATE OR REPLACE FUNCTION fn_validar_recursos_viaje() RETURNS trigger AS $$
DECLARE
    v_bus      bus_ref%ROWTYPE;
    v_chofer   chofer_ref%ROWTYPE;
    v_cambia_bus    BOOLEAN;
    v_cambia_chofer BOOLEAN;
BEGIN
    IF TG_OP = 'INSERT' THEN
        v_cambia_bus := NEW.id_bus IS NOT NULL;
        v_cambia_chofer := NEW.id_chofer IS NOT NULL;
    ELSE
        v_cambia_bus := NEW.id_bus IS NOT NULL AND NEW.id_bus IS DISTINCT FROM OLD.id_bus;
        v_cambia_chofer := NEW.id_chofer IS NOT NULL AND (
               NEW.id_chofer IS DISTINCT FROM OLD.id_chofer
            OR NEW.fecha_hora_llegada_estimada IS DISTINCT FROM OLD.fecha_hora_llegada_estimada);
    END IF;

    IF v_cambia_bus THEN
        SELECT * INTO v_bus FROM bus_ref WHERE id = NEW.id_bus;
        IF FOUND AND NOT v_bus.activo THEN
            RAISE EXCEPTION 'El bus asignado está inactivo';
        END IF;
        IF FOUND AND v_bus.id_sucursal <> NEW.id_sucursal_origen THEN
            RAISE EXCEPTION 'El bus no pertenece a la sucursal del viaje';
        END IF;
        -- EN_TRANSITO sí se permite: un bus en ruta puede tener viajes futuros
        IF FOUND AND v_bus.estado_operativo IN ('EN_TALLER','FUERA_DE_SERVICIO') THEN
            RAISE EXCEPTION 'El bus está en taller o fuera de servicio';
        END IF;
        IF FOUND AND NEW.numero_pasajeros IS NOT NULL AND v_bus.capacidad_pasajeros < NEW.numero_pasajeros THEN
            RAISE EXCEPTION 'El bus (% asientos) no alcanza para % pasajeros', v_bus.capacidad_pasajeros, NEW.numero_pasajeros;
        END IF;
    END IF;

    IF v_cambia_chofer THEN
        SELECT * INTO v_chofer FROM chofer_ref WHERE id = NEW.id_chofer;
        IF FOUND AND NOT v_chofer.activo THEN
            RAISE EXCEPTION 'El chofer asignado está inactivo';
        END IF;
        IF FOUND AND v_chofer.id_sucursal <> NEW.id_sucursal_origen THEN
            RAISE EXCEPTION 'El chofer no pertenece a la sucursal del viaje';
        END IF;
        IF FOUND AND v_chofer.fecha_vencimiento_licencia < NEW.fecha_hora_llegada_estimada::date THEN
            RAISE EXCEPTION 'La licencia del chofer vence antes de finalizar el viaje';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_viaje_validar_recursos BEFORE INSERT OR UPDATE ON viaje
    FOR EACH ROW EXECUTE FUNCTION fn_validar_recursos_viaje();

-- Ciclo de vida del viaje: un alquiler nace PENDIENTE_ASIGNACION; uno regular, PROGRAMADO
CREATE TRIGGER trg_viaje_transicion BEFORE UPDATE OF estado ON viaje
    FOR EACH ROW EXECUTE FUNCTION fn_validar_transicion_estado(
        'estado',
        'PENDIENTE_ASIGNACION>PROGRAMADO',
        'PROGRAMADO>EN_TRANSITO',
        'EN_TRANSITO>FINALIZADO');

-- Registros inmutables de salida y llegada
CREATE TABLE registro_salida (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_viaje                 UUID          NOT NULL UNIQUE REFERENCES viaje(id) ON DELETE RESTRICT,
    id_bus                   UUID          NOT NULL,
    id_chofer                UUID          NOT NULL,
    fecha_hora_salida_real   TIMESTAMPTZ   NOT NULL,
    kilometraje_inicial      NUMERIC(10,1) NOT NULL CHECK (kilometraje_inicial >= 0),
    registrado_por           UUID          NOT NULL,           -- administrador de sucursal o chofer
    registrado_en            TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE registro_llegada (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_viaje                  UUID          NOT NULL UNIQUE REFERENCES viaje(id) ON DELETE RESTRICT,
    fecha_hora_llegada_real   TIMESTAMPTZ   NOT NULL,
    kilometraje_final         NUMERIC(10,1) NOT NULL CHECK (kilometraje_final >= 0),
    distancia_recorrida_km    NUMERIC(10,1) NOT NULL CHECK (distancia_recorrida_km >= 0),
    gasto_combustible_total   NUMERIC(10,2) NOT NULL CHECK (gasto_combustible_total >= 0),
    salario_base_chofer       NUMERIC(10,2) NOT NULL,          -- instantánea al momento de la llegada
    registrado_por            UUID          NOT NULL,
    registrado_en             TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TRIGGER trg_salida_inmutable  BEFORE UPDATE OR DELETE ON registro_salida
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_modificacion();
CREATE TRIGGER trg_llegada_inmutable BEFORE UPDATE OR DELETE ON registro_llegada
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_modificacion();
