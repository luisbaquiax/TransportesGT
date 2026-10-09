-- bd_boletos — Boletos (modelo-datos-postgresql-v4.md)

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

-- Proyección de viajes REGULARES (alimentada por ViajeProgramado/Actualizado/Eliminado)
CREATE TABLE viaje_ref (
    id                          UUID PRIMARY KEY,
    id_ruta                     UUID          NOT NULL,
    id_sucursal_origen          UUID          NOT NULL,
    nombre_origen               VARCHAR(150)  NOT NULL,
    nombre_destino              VARCHAR(150)  NOT NULL,
    fecha_hora_salida           TIMESTAMPTZ   NOT NULL,
    fecha_hora_llegada_estimada TIMESTAMPTZ   NOT NULL,
    precio_boleto               NUMERIC(10,2) NOT NULL,
    id_bus                      UUID,
    placa_bus                   VARCHAR(15),
    capacidad_bus               SMALLINT,
    estado                      VARCHAR(25)   NOT NULL,
    eliminado                   BOOLEAN       NOT NULL DEFAULT FALSE,
    version                     BIGINT        NOT NULL
);
CREATE INDEX idx_viaje_ref_salida ON viaje_ref (fecha_hora_salida) WHERE NOT eliminado;

-- Perfil comercial del cliente (alimentada por PerfilClienteActualizado, publicado por Clientes y Cartera)
CREATE TABLE cliente_ref (
    id_usuario       UUID PRIMARY KEY,
    nit              VARCHAR(20),                  -- normalizado; null mientras el perfil no tenga NIT
    nombre_completo  VARCHAR(150) NOT NULL,
    activo           BOOLEAN      NOT NULL,
    version          BIGINT       NOT NULL
);
CREATE INDEX idx_cliente_ref_nit ON cliente_ref (nit) WHERE nit IS NOT NULL;

CREATE TABLE compra (
    id                      UUID PRIMARY KEY,               -- idCompra, generado por el cliente/servicio (idempotencia)
    id_viaje                UUID          NOT NULL REFERENCES viaje_ref(id),
    canal                   VARCHAR(10)   NOT NULL CHECK (canal IN ('EN_LINEA','MOSTRADOR')),
    estado                  VARCHAR(12)   NOT NULL DEFAULT 'RESERVADA'
        CHECK (estado IN ('RESERVADA','CONFIRMADA','ANULADA')),
    cantidad_boletos        SMALLINT      NOT NULL CHECK (cantidad_boletos > 0),
    precio_unitario         NUMERIC(10,2) NOT NULL CHECK (precio_unitario >= 0),
    monto_total             NUMERIC(12,2) NOT NULL CHECK (monto_total >= 0),
    id_usuario_comprador    UUID,                            -- null en venta presencial sin cuenta
    nit_comprador           VARCHAR(20)   NOT NULL DEFAULT 'CF',
    nombre_comprador        VARCHAR(150)  NOT NULL,
    id_pago                 UUID,                            -- movimiento de cartera (solo EN_LINEA)
    fecha_pago              DATE,                            -- elegida por el usuario
    id_sucursal_ingreso     UUID          NOT NULL,
    id_usuario_vendedor     UUID,                            -- cajero / administrador (MOSTRADOR)
    reserva_expira_en       TIMESTAMPTZ,                     -- para liberar reservas sin pago
    motivo_anulacion        VARCHAR(30),
    version                 BIGINT        NOT NULL DEFAULT 0,
    creado_en               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    confirmado_en           TIMESTAMPTZ,
    anulado_en              TIMESTAMPTZ,
    CONSTRAINT ck_compra_pago CHECK (
        estado <> 'CONFIRMADA' OR fecha_pago IS NOT NULL
    ),
    CONSTRAINT ck_compra_en_linea CHECK (
        canal <> 'EN_LINEA' OR id_usuario_comprador IS NOT NULL
    )
);
CREATE INDEX idx_compra_viaje   ON compra (id_viaje);
CREATE INDEX idx_compra_nit     ON compra (nit_comprador);          -- historial por NIT
CREATE INDEX idx_compra_usuario ON compra (id_usuario_comprador);
CREATE INDEX idx_compra_reservas_vencidas ON compra (reserva_expira_en) WHERE estado = 'RESERVADA';

CREATE TABLE boleto (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_compra       UUID         NOT NULL REFERENCES compra(id),
    id_viaje        UUID         NOT NULL REFERENCES viaje_ref(id),
    numero_asiento  SMALLINT     NOT NULL CHECK (numero_asiento > 0),
    estado          VARCHAR(12)  NOT NULL DEFAULT 'RESERVADO'
        CHECK (estado IN ('RESERVADO','CONFIRMADO','ANULADO')),
    codigo          VARCHAR(20)  NOT NULL UNIQUE,
    creado_en       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- GARANTÍA DE CONCURRENCIA: un asiento no puede estar activo dos veces en el mismo viaje.
-- Los boletos ANULADOS quedan fuera del índice, por lo que el asiento se libera solo.
CREATE UNIQUE INDEX uq_asiento_activo
    ON boleto (id_viaje, numero_asiento)
    WHERE estado IN ('RESERVADO','CONFIRMADO');
CREATE INDEX idx_boleto_compra ON boleto (id_compra);

-- El asiento debe existir dentro de la capacidad del bus asignado al viaje
CREATE OR REPLACE FUNCTION fn_validar_asiento() RETURNS trigger AS $$
DECLARE
    v_capacidad SMALLINT;
BEGIN
    SELECT capacidad_bus INTO v_capacidad FROM viaje_ref WHERE id = NEW.id_viaje;
    IF v_capacidad IS NULL THEN
        RAISE EXCEPTION 'El viaje aún no tiene bus asignado';
    END IF;
    IF NEW.numero_asiento > v_capacidad THEN
        RAISE EXCEPTION 'El asiento % excede la capacidad del bus (%)', NEW.numero_asiento, v_capacidad;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_boleto_validar_asiento BEFORE INSERT ON boleto
    FOR EACH ROW EXECUTE FUNCTION fn_validar_asiento();

-- Estados válidos de compra y boleto
CREATE TRIGGER trg_compra_transicion BEFORE UPDATE OF estado ON compra
    FOR EACH ROW EXECUTE FUNCTION fn_validar_transicion_estado(
        'estado', 'RESERVADA>CONFIRMADA', 'RESERVADA>ANULADA', 'CONFIRMADA>ANULADA');

CREATE TRIGGER trg_boleto_transicion BEFORE UPDATE OF estado ON boleto
    FOR EACH ROW EXECUTE FUNCTION fn_validar_transicion_estado(
        'estado', 'RESERVADO>CONFIRMADO', 'RESERVADO>ANULADO', 'CONFIRMADO>ANULADO');
