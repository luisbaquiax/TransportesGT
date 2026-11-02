# Modelo de datos PostgreSQL — por microservicio

Un esquema por servicio, alineado con `catalogo-eventos-es-v4.md` (mismos nombres y valores de enumeración). Pensado para un script SQL de esquema por servicio (`esquema.sql`, o `V1__esquema_inicial.sql` si se usa Flyway) y mapeo con **JPA/Hibernate**. Los *triggers*, las funciones y los `EXCLUDE` de este documento no los genera Hibernate: deben ejecutarse como SQL.

### Cambios de la v4 respecto a la v3

- **Identidad:** `token_contrasena` con la opción C: columna `invalidado_en`, restricción `ck_token_estado` e índice único parcial `uq_token_activo` (un solo token activo por usuario y propósito). `expira_en` ya no se reescribe nunca. Es lo que ya implementa el servicio.
- **Perfil del cliente en otros servicios:** nuevo evento `PerfilClienteActualizado` (Clientes y Cartera) y tabla `cliente_ref` en **Boletos** y **Alquileres**, para obtener NIT y nombre sin llamadas síncronas. NIT normalizado (`Nit.normalizar()`) y `'CF'` prohibido en el perfil. 32 tipos de evento.
- **Clientes y Cartera:** columna `version_usuario` (descarta `UsuarioActualizado` viejos o desordenados), `CHECK` del NIT, reglas de reembolso (por pago original, aun con el cliente inactivo, fecha en hora de Guatemala) y `saldoPosterior` en los eventos de cartera.
- **Viajes:** `bus_ref.estado_operativo` (se rechazan buses `EN_TALLER` y `FUERA_DE_SERVICIO`), `viaje.numero_pasajeros` (el bus de un alquiler debe tener capacidad suficiente) y `ViajeActualizado` también al registrar salida y llegada.
- **Boletos:** no vende viajes que no estén `PROGRAMADO` ni con salida ya pasada.
- **Alquileres:** `distancia_estimada_km`, coordenadas opcionales de origen y destino (mapa) y tabla `tarifa_alquiler` (precio por km y por día, configurados por el administrador de sistema) para calcular el precio estimado.
- **Flota:** un chofer solo se desactiva si no tiene viajes activos; el kilometraje nunca baja (`GREATEST`).
- **Costos:** tarifa de depreciación inicial en los datos iniciales; un segundo cambio de tarifa el mismo día reemplaza al primero.
- **Reportes:** el estado operativo del bus se deriva de `SalidaRegistrada` y `LlegadaRegistrada`.

### Cambios de la v3 respecto a la v2

- **Contraseñas (Identidad):** tabla nueva `token_contrasena` (cambio, restablecimiento y activación de cuenta) y columna `usuario.contrasena_cambiada_en`. Eventos nuevos `EnlaceContrasenaEmitido` y `ContrasenaCambiada` (31 tipos de evento en total).
- **Chofer con usuario:** `chofer_ref` (Viajes) incluye `id_usuario`, para que solo el chofer asignado pueda registrar la salida y la llegada de su viaje.
- **Cambio de bus con boletos vendidos:** Viajes consulta de forma síncrona a Boletos el mayor asiento vendido (`GET /boletos/viajes/{idViaje}/asiento-maximo`) y rechaza un bus con menor capacidad.
- **Notificaciones:** nuevos tipos en la bitácora; el cuerpo del correo con enlace de contraseña no se guarda.
- **Outbox:** *job* de limpieza de filas publicadas.

### Cambios de la v2 respecto a la v1

- **Transiciones de estado** validadas en la base (`fn_validar_transicion_estado`) para viaje, compra, boleto y alquiler.
- **Gastos de taller inmutables** y total del encabezado = suma de sus líneas (trigger diferido).
- **Asiento ≤ capacidad del bus** validado por trigger.
- **Administrador de sucursal** solo creado por un administrador de sistema activo (trigger).
- **Salario del chofer = gasto:** `pago_chofer` deja de ser condicional y es inmutable.
- **Alquiler:** nueva columna `fecha_hora_llegada_estimada` (un alquiler de ida y vuelta genera un solo viaje).

---

## 0. Criterios generales

| Criterio | Decisión |
|---|---|
| **Aislamiento** | Una **base de datos por servicio** en un mismo servidor PostgreSQL, con un usuario distinto que solo tiene permisos sobre su propia base. |
| **Claves primarias** | `UUID` generado por la aplicación (`gen_random_uuid()` como valor por defecto, PostgreSQL 13+). Permite crear el id antes de guardar y usarlo en eventos. |
| **Claves foráneas entre servicios** | **No existen.** Se guarda el `id` de la entidad externa (`UUID` sin `REFERENCES`). La consistencia se logra con eventos y proyecciones locales. |
| **Dinero** | `NUMERIC(12,2)`. Nunca `FLOAT`. |
| **Fechas** | `DATE` para fechas de negocio elegidas por el usuario (`fecha_pago`, `fecha_servicio`...). `TIMESTAMPTZ` para fecha-hora real (`fecha_hora_salida_real`) y auditoría. |
| **Enumeraciones** | `VARCHAR` + `CHECK` (más simple de migrar que el tipo `ENUM` de PostgreSQL). |
| **Concurrencia optimista** | Columna `version BIGINT` (`@Version` en JPA). En tablas `_ref` guarda la `versionAgregado` del último evento aplicado. |
| **No se elimina, se desactiva** | Usuarios, sucursales, buses, choferes y categorías llevan `activo`/`activa`. Triggers impiden el `DELETE`. |
| **Nombres** | `snake_case` en español, sin tildes ni eñes. |

### Tablas con sufijo `_ref`

Son **proyecciones locales** de datos que pertenecen a otro servicio, alimentadas por eventos (`UPSERT` comparando `version`). El servicio **nunca las modifica por su cuenta**.

### Crear bases de datos y usuarios

`CREATE DATABASE` **no puede ejecutarse dentro de un bloque `DO` ni de una función** (PostgreSQL lo rechaza porque corre en una transacción). Se ejecuta una sentencia por vez, por ejemplo con este script (una sola vez, como superusuario):

```bash
for s in identidad flota viajes boletos clientes alquileres costos notificaciones reportes; do
  psql -U postgres -c "CREATE ROLE svc_$s LOGIN PASSWORD 'cambiar_en_produccion'"
  psql -U postgres -c "CREATE DATABASE bd_$s OWNER svc_$s"
  psql -U postgres -c "REVOKE ALL ON DATABASE bd_$s FROM PUBLIC"
done
```

Con Docker Compose también sirve montar un script `.sh` equivalente en `/docker-entrypoint-initdb.d/`.

### Tablas comunes (en las 9 bases)

Cada servicio incluye estas dos tablas en su migración inicial.

```sql
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
-- Job de limpieza (diario): borrar filas publicadas con más de 24 h de antigüedad
--   DELETE FROM outbox_evento WHERE publicado_en < now() - interval '24 hours';

-- Consumidor idempotente: evita procesar dos veces el mismo evento
CREATE TABLE evento_procesado (
    id_evento    UUID         NOT NULL,
    consumidor   VARCHAR(100) NOT NULL,                -- nombre del listener
    procesado_en TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (id_evento, consumidor)
);
```

### Funciones auxiliares (copiar a las bases donde se usan)

```sql
-- Bloquea UPDATE y DELETE (registros inmutables)
CREATE OR REPLACE FUNCTION fn_bloquear_modificacion() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Los registros de % son inmutables: no se pueden modificar ni eliminar', TG_TABLE_NAME
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;

-- Bloquea solo DELETE (los registros se desactivan, no se eliminan)
CREATE OR REPLACE FUNCTION fn_bloquear_eliminacion() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Los registros de % no se eliminan, solo se desactivan', TG_TABLE_NAME
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
```

---

## 1. `bd_identidad` — Identidad y Administración

**Responsabilidad:** usuarios, roles, sucursales. Dueño de `Sucursal*` y `Usuario*`.

```sql
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
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_usuario    UUID         NOT NULL REFERENCES usuario(id),
    hash_token    CHAR(64)     NOT NULL UNIQUE,     -- SHA-256 (hex) del token: el token en claro NO se guarda
    proposito     VARCHAR(20)  NOT NULL
        CHECK (proposito IN ('RESTABLECIMIENTO','ACTIVACION')),
    expira_en     TIMESTAMPTZ  NOT NULL,            -- nunca se modifica después de crear el token
    usado_en      TIMESTAMPTZ,                      -- se llena al consumirlo: no se puede reutilizar
    invalidado_en TIMESTAMPTZ,                      -- se llena cuando otro token más nuevo lo reemplaza
    ip_solicitud  VARCHAR(45),                      -- auditoría (IPv4 o IPv6)
    creado_en     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_token_vigencia CHECK (expira_en > creado_en),
    CONSTRAINT ck_token_estado   CHECK (usado_en IS NULL OR invalidado_en IS NULL)   -- no puede estar usado e invalidado a la vez
);
CREATE INDEX idx_token_usuario ON token_contrasena (id_usuario, creado_en DESC);
CREATE INDEX idx_token_expira  ON token_contrasena (expira_en);                    -- para el job de limpieza

-- Un solo token activo por usuario y propósito (garantía de la base, aun con solicitudes simultáneas)
CREATE UNIQUE INDEX uq_token_activo ON token_contrasena (id_usuario, proposito)
    WHERE usado_en IS NULL AND invalidado_en IS NULL;

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
```

**Reglas que quedan en la aplicación:**
- Solo un `ADMIN_SISTEMA` puede crear un `ADMIN_SUCURSAL`: lo refuerza el trigger `trg_admin_sucursal_creador` (usa `creado_por`) y la aplicación comprueba además el rol del token. El primer administrador de sistema se crea con un script de datos iniciales (*seed*) con id fijo `b0000000-0000-0000-0000-000000000001`; ese mismo UUID es el `creado_por` de los datos iniciales de `configuracion_depreciacion` (Costos) y `tarifa_alquiler` (Alquileres).
- Cada sucursal debe tener **al menos un** `ADMIN_SUCURSAL` activo: la aplicación lo crea junto con la sucursal en la misma transacción, y el trigger `fn_proteger_ultimo_admin_sucursal` impide desactivar, reasignar o cambiar de rol al último, incluso con operaciones concurrentes (bloqueo por sucursal).
- Autenticación con JWT (sin tabla de sesiones). Si se agrega *refresh token*, crear `token_refresco(id, id_usuario, hash, expira_en, revocado)`. Limitación aceptada: un JWT ya emitido no se puede revocar al cambiar la contraseña; dura lo que dure su vigencia (30–60 min).
- **Contraseñas:** BCrypt, política mínima de 8 caracteres con mayúscula, minúscula y número (`@Pattern`). Un token es **vigente** si `usado_en IS NULL AND invalidado_en IS NULL AND expira_en > now()`; vigencia de 30 min (`RESTABLECIMIENTO`) y 48 h (`ACTIVACION`), configurable. Al emitir uno nuevo para el mismo usuario y propósito, en la misma transacción primero se invalidan los anteriores (`invalidado_en = now()`, incluidos los vencidos sin usar; `expira_en` no se toca) y luego se inserta el nuevo. Si dos solicitudes simultáneas chocan con `uq_token_activo`, la segunda responde el mismo `202` sin crear evento (capturar la excepción fuera de la transacción).
- **Cambiar contraseña** (`POST /auth/cambiar-contrasena`, con sesión): exige la contraseña actual, que la nueva cumpla la política y sea distinta; publica `ContrasenaCambiada` (motivo `CAMBIO`).
- **Olvidé mi contraseña** (`POST /auth/olvide-contrasena`): responde **siempre 202** con el mismo mensaje (no revela si el correo existe); solo si el usuario existe, está activo y no pasó de **3 solicitudes por hora** (se cuenta en `token_contrasena`) genera un token de 32 bytes (`SecureRandom`, Base64 URL-safe), guarda su SHA-256 y publica `EnlaceContrasenaEmitido` por outbox en la misma transacción.
- **Validar y restablecer** (`GET /auth/enlace-contrasena/validar?token=` → 200 o 410; `POST /auth/restablecer-contrasena`): en una transacción valida el token, actualiza `hash_contrasena` y `contrasena_cambiada_en`, marca `usado_en`, invalida los demás tokens vigentes y publica `ContrasenaCambiada`.
- **Activación de cuenta:** al crear un `ADMIN_SUCURSAL`, `CAJERO` o `CHOFER`, se guarda un hash BCrypt de un valor aleatorio (nadie puede iniciar sesión) y se emite un token `ACTIVACION`. Los datos de prueba (*seed*) llevan contraseñas ya definidas.
- Un *job* diario borra los tokens cuyo `expira_en` pasó hace más de 7 días.

---

## 2. `bd_flota` — Flota

**Responsabilidad:** buses y choferes. Dueño de `Bus*` y `Chofer*`.

```sql
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
```

**Reglas que quedan en la aplicación:**
- Desactivar un bus solo si **no** tiene viajes programados o en tránsito → `GET /viajes/activos?idBus=` (REST a Viajes). La misma regla vale para un **chofer** → `GET /viajes/activos?idChofer=` (409 `CHOFER_CON_VIAJES`).
- Cada bus y cada chofer pertenecen a **una sola** sucursal, y el administrador de sucursal solo gestiona los suyos.
- `kilometraje_actual` y `estado_operativo` se actualizan al consumir `SalidaRegistrada` / `LlegadaRegistrada`. El kilometraje nunca baja si los eventos llegan desordenados: `kilometraje_actual = GREATEST(kilometraje_actual, kilometraje_final)`. Estos cambios automáticos **no** publican `BusActualizado`; los cambios hechos por el administrador (por ejemplo, enviar un bus a `EN_TALLER`) sí.
- El chofer queda ligado a su usuario por `chofer.id_usuario`. No se valida contra Identidad: al registrar o actualizar el chofer se publica `ChoferRegistrado`/`ChoferActualizado` y Viajes actualiza su `chofer_ref`.
- Al crear o actualizar un chofer se publica `ChoferRegistrado` / `ChoferActualizado` (de ahí salen los avisos de licencia).

---

## 3. `bd_viajes` — Rutas y Viajes

**Responsabilidad:** rutas, viajes (regulares y de alquiler), registros inmutables de salida y llegada. Dueño de `Ruta*`, `Viaje*`, `Salida*`, `Llegada*`.

```sql
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
```

**Reglas que quedan en la aplicación:**
- La ruta solo la gestiona el administrador de la **sucursal de origen** (validar contra `id_sucursal_origen` del token). Se elimina solo si no tiene viajes (la FK `ON DELETE RESTRICT` lo refuerza).
- Un viaje solo se elimina si **no** tiene registro de salida (`registro_salida` con `RESTRICT` lo refuerza) y se publica `ViajeEliminado` por outbox.
- **Autorización del chofer:** un usuario con rol `CHOFER` solo puede registrar la salida y la llegada de un viaje cuyo `chofer_ref.id_usuario` sea el suyo (la aplicación compara con el `idUsuario` del token).
- **Cambio de bus:** al cambiar el bus de un viaje que ya tiene boletos, Viajes llama de forma síncrona a Boletos (`GET /boletos/viajes/{idViaje}/asiento-maximo`, con *timeout* y Resilience4j) y rechaza el bus si `capacidad < asiento máximo vendido`. Si Boletos no responde, se rechaza el cambio (falla cerrada).
- Se valida `kilometraje_final >= kilometraje_inicial` y se calcula `distancia_recorrida_km` **antes** de insertar (los registros no se pueden corregir).
- El bus y el chofer deben estar activos, pertenecer a la sucursal correcta y (el chofer) tener la licencia vigente durante el viaje: lo refuerza el trigger `fn_validar_recursos_viaje`, y el `EXCLUDE` impide los traslapes. Como las réplicas `_ref` pueden estar desactualizadas unos segundos, si Flota desactiva un bus que ya tiene viajes futuros, Viajes lo detecta al consumir `BusActualizado` y lo muestra en el listado de pendientes del administrador de sucursal.
- **`ViajeActualizado` también al avanzar el viaje:** al registrar la salida (`EN_TRANSITO`) y la llegada (`FINALIZADO`), Viajes incrementa `version`, actualiza `viaje.estado` y publica `ViajeActualizado` en la misma transacción. Así Boletos y Alquileres reciben el estado real.
- `GET /viajes/activos` acepta `idBus` **o** `idChofer` (viajes `PROGRAMADO` o `EN_TRANSITO`).
- `bus_ref.estado_operativo` solo cambia con `BusActualizado` (cambios del administrador). El paso automático `EN_TRANSITO` ↔ `DISPONIBLE` no se publica, por eso solo se rechazan `EN_TALLER` y `FUERA_DE_SERVICIO`.
- Al consumir `AlquilerConfirmado` se inserta un viaje `ALQUILER_PRIVADO` en `PENDIENTE_ASIGNACION` con su `numero_pasajeros` (idempotente por `id_alquiler UNIQUE`). Un alquiler de ida y vuelta genera **un único viaje**: su `fecha_hora_llegada_estimada` (la fija el administrador al cotizar) es el regreso al origen, y el `EXCLUDE` reserva el bus durante todo el intervalo.

---

## 4. `bd_boletos` — Boletos

**Responsabilidad:** compras, boletos y asientos. Dueño de `VentaBoletos*` y `Boletos*`.

```sql
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
```

**Reglas que quedan en la aplicación:**
- `numero_asiento <= capacidad_bus`: lo valida el trigger `fn_validar_asiento` contra `viaje_ref.capacidad_bus`.
- Flujo de compra: insertar `compra` + `boleto` en `RESERVADO` (si el asiento ya está tomado, el índice único lanza error → se responde "asiento no disponible") → cobrar en Cartera → `CONFIRMADO` + evento por outbox. Si el pago falla → `ANULADO`.
- Un *job* anula las reservas con `reserva_expira_en` vencida (previa consulta del estado del pago).
- Al consumir `ViajeEliminado`: marcar `compra`/`boleto` como `ANULADO` y publicar un `BoletosAnulados` por compra.
- **Compra en línea:** el NIT y el nombre del comprador salen de `cliente_ref` (usuario autenticado), nunca del cuerpo de la petición. Si el perfil no tiene NIT → `422 PERFIL_INCOMPLETO` antes de reservar. Los NIT se guardan normalizados (`Nit.normalizar()`: mayúsculas, sin guiones ni espacios).
- **Historial:** `SELECT ... FROM compra WHERE id_usuario_comprador = :idUsuario OR nit_comprador = :nit`, con `:nit` tomado de `cliente_ref` del usuario autenticado (nunca de un parámetro libre). Incluye las ventas de mostrador hechas con su NIT antes de crear la cuenta y no pierde las compras en línea si el cliente cambia de NIT.
- **Solo se venden viajes futuros y programados:** se rechaza la venta si `viaje_ref.estado <> 'PROGRAMADO'` o si `fecha_hora_salida <= now()`. `viaje_ref.estado` se mantiene al día con `ViajeActualizado` (que Viajes publica también al registrar salida y llegada).
- Venta presencial sin NIT → `nit_comprador = 'CF'`.
- **Consulta para Viajes:** `GET /boletos/viajes/{idViaje}/asiento-maximo` devuelve `MAX(numero_asiento)` de los boletos `RESERVADO` o `CONFIRMADO` del viaje (0 si no hay), apoyado en `uq_asiento_activo`.

---

## 5. `bd_clientes` — Clientes y Cartera

**Responsabilidad:** perfiles de cliente y cartera digital. Dueño de `Cartera*`.

```sql
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

-- El libro de movimientos es inmutable
CREATE TRIGGER trg_movimiento_inmutable BEFORE UPDATE OR DELETE ON movimiento_cartera
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_modificacion();
```

**Reglas que quedan en la aplicación:**
- Al consumir `UsuarioCreado` de **cualquier rol** se crean `cliente` y `cartera` (saldo 0) en una sola transacción, idempotente por `id_usuario UNIQUE`. El enunciado dice que *todos* los usuarios tienen perfil (NIT, DPI, teléfono y dirección) y que todos pueden ser clientes de cualquier sucursal. Los datos de identidad (correo, contraseña, rol) quedan en Identidad; el perfil comercial, aquí. `UsuarioActualizado` solo se aplica si su `versionAgregado` es mayor que `version_usuario` (descarta eventos viejos o desordenados) y actualiza nombre, correo y `activo`.
- Pago: `SELECT ... FOR UPDATE` (o `@Version`) sobre `cartera`, validar saldo, insertar `PAGO`, descontar. Si ya existe un `PAGO` con la misma referencia, se devuelve el existente (idempotencia).
- Reembolso al consumir `BoletosAnulados` (con `canal = EN_LINEA`) o `AlquilerCancelado` (con `idPago`): inserta `REEMBOLSO` (índice único = idempotente) y publica `CarteraReembolsada` (con `saldoPosterior`). El pago original se busca por `idPago`, o por `(tipo_referencia, id_referencia)` si no viene; **si no existe un `PAGO`, el evento se ignora** (p. ej. una compra anulada mientras estaba `RESERVADA`). El reembolso se aplica **aunque el cliente esté inactivo** (un cliente desactivado no pierde su dinero); en cambio, pagos y recargas sí se bloquean. `fecha_movimiento` del reembolso = fecha en hora de Guatemala de `canceladoEn` (alquiler) u `ocurridoEn` (boletos). El movimiento, el registro en `evento_procesado` y el evento de salida (outbox) se guardan en **la misma transacción**: si el consumidor cae después de confirmar, la reentrega del evento se ignora y no hay doble reembolso.
- `nit` es obligatorio para comprar o alquilar en línea (se valida al pagar). Se guarda normalizado con `Nit.normalizar()` y `'CF'` se rechaza en el perfil (`CF` solo existe en ventas y alquileres de mostrador).
- **`PerfilClienteActualizado`:** se publica por outbox (misma transacción) al crear el cliente desde `UsuarioCreado` (con `nit = null`) y cada vez que cambian `nombre_completo`, `nit` o `activo`. `idAgregado = id_usuario` y `versionAgregado = cliente.version`. Lo consumen Boletos y Alquileres (`cliente_ref`).
- `CarteraRecargada` y `CarteraReembolsada` llevan `saldoPosterior` (el saldo que queda tras el movimiento) para que el correo lo muestre.
- El cliente se crea **solo** al consumir `UsuarioCreado`; no se crea de forma perezosa desde el JWT.
- **Recarga idempotente:** `POST /cartera/recargas` exige el encabezado `Clave-Idempotencia`, que se guarda en `movimiento_cartera.clave_idempotencia` (UNIQUE). Si se repite, devuelve la recarga original sin sumar el saldo dos veces.

---

## 6. `bd_alquileres` — Alquileres Privados

**Responsabilidad:** solicitud, cotización, pago y seguimiento del alquiler. Dueño de `Alquiler*`.

```sql
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
    -- Coordenadas elegidas en el mapa (Leaflet). Opcionales: si no se implementa el mapa, quedan en NULL
    latitud_origen       NUMERIC(9,6)  CHECK (latitud_origen  BETWEEN -90  AND 90),
    longitud_origen      NUMERIC(9,6)  CHECK (longitud_origen BETWEEN -180 AND 180),
    latitud_destino      NUMERIC(9,6)  CHECK (latitud_destino  BETWEEN -90  AND 90),
    longitud_destino     NUMERIC(9,6)  CHECK (longitud_destino BETWEEN -180 AND 180),
    distancia_estimada_km NUMERIC(8,2)  NOT NULL CHECK (distancia_estimada_km > 0),   -- con coordenadas la calcula el backend; si no, la indica el solicitante. El administrador puede corregirla al cotizar
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
    -- las cuatro coordenadas vienen juntas o ninguna
    CONSTRAINT ck_alquiler_coordenadas CHECK (
        (latitud_origen IS NULL AND longitud_origen IS NULL AND latitud_destino IS NULL AND longitud_destino IS NULL)
     OR (latitud_origen IS NOT NULL AND longitud_origen IS NOT NULL AND latitud_destino IS NOT NULL AND longitud_destino IS NOT NULL)
    ),
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
```

**Reglas que quedan en la aplicación:**
- Flujo de estados: `SOLICITADO → COTIZADO → CONFIRMADO → FINALIZADO` (o `CANCELADO`). Solo se paga en `COTIZADO`.
- Pago: REST a Cartera con `Clave-Idempotencia = id del alquiler`; si responde OK, `CONFIRMADO` + `AlquilerConfirmado` por outbox (misma transacción).
- Al consumir `ViajeProgramado` con `idAlquiler`: llenar `id_viaje`. Con `ViajeActualizado`: llenar `id_bus`, `placa_bus`, `id_chofer`, `nombre_chofer`. Con `LlegadaRegistrada`: pasar a `FINALIZADO`. Con `ViajeEliminado` (solo se puede eliminar un viaje no iniciado): pasar el alquiler a `CANCELADO` (`motivo_cancelacion = 'VIAJE_ELIMINADO'`) y publicar `AlquilerCancelado`. Si hubo pago en cartera, Clientes y Cartera lo reembolsa y Reportes revierte el ingreso.
- **Ida y vuelta = un solo viaje.** Al cotizar, el administrador fija el precio **y** `fecha_hora_llegada_estimada` (regreso al origen si es ida y vuelta). Sin esos datos el alquiler no puede pasar a `CONFIRMADO`.
- Alquiler solicitado en `MOSTRADOR` sin cuenta: se paga en el mostrador (sin `id_pago`, con `fecha_pago` indicada por el usuario) y se enlaza por NIT, para que aparezca en el historial si el cliente crea su cuenta después. Si el cliente no da NIT, se usa `nit_cliente = 'CF'` (igual que en Boletos).
- **Cálculo del precio estimado** (supuesto propio: el enunciado no da fórmula): `distancia_estimada_km × precio_por_km × (2 si es ida y vuelta) + días × precio_por_dia`. `días` = 1 para solo ida; en ida y vuelta, los días de calendario entre salida y retorno, ambos inclusive. Se usa la tarifa más reciente con `vigente_desde <= hoy`. El resultado se guarda como `ESTIMADA` en `cotizacion_alquiler` (con las tarifas usadas en `comentario`); el administrador de sucursal lo confirma o lo modifica (`AJUSTADA_ADMIN`).
- **Distancia (selección en mapa, opcional):** el frontend muestra un mapa Leaflet (tiles de OpenStreetMap, sin clave) donde el cliente marca origen y destino. Si la solicitud trae las cuatro coordenadas, el backend **ignora** la `distanciaEstimadaKm` enviada y la calcula: distancia Haversine (línea recta) × factor de carretera, redondeada a 2 decimales. El factor es configurable (`alquileres.factor-carretera`, por omisión **1.3**; supuesto propio, porque la línea recta subestima el recorrido). Si no trae coordenadas, `distanciaEstimadaKm` es obligatoria (respaldo manual). En ambos casos el administrador puede corregirla al cotizar. Las coordenadas no viajan en eventos: ningún otro servicio las necesita.
- **Tarifas:** las cambia solo el `ADMIN_SISTEMA` (`POST /alquileres/tarifas`, `GET /alquileres/tarifas/vigente`). Si ya existe una fila con la misma `vigente_desde`, se reemplaza. Un cambio de tarifa no altera las cotizaciones ya guardadas. No genera evento: solo Alquileres usa estas tarifas.
- En alquiler **en línea**, `nit_cliente` y `nombre_cliente` salen de `cliente_ref` del usuario autenticado (sin NIT en el perfil → `422 PERFIL_INCOMPLETO`).

---

## 7. `bd_costos` — Costos Operativos

**Responsabilidad:** combustible, depreciación, taller y repuestos, categorías de gasto, salario del chofer. Dueño de `Gasto*`, `Depreciacion*`, `Pago*`.

```sql
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
```

**Reglas que quedan en la aplicación:**
- **Categorías:** el enunciado prohíbe eliminar una categoría **con gastos asociados** (solo se desactiva, `activa = false`); la FK `RESTRICT` lo garantiza en la base. Una categoría sin gastos sí puede eliminarse. Las categorías nuevas se agregan por API, sin cambiar código.
- `gasto_taller.total` debe ser igual a la suma de sus líneas: el servicio lo calcula y el trigger diferido `trg_taller_total` lo verifica al confirmar la transacción. Debe haber **al menos una línea** (lo valida la aplicación). Los gastos de taller no se editan ni se eliminan.
- La depreciación al consumir `LlegadaRegistrada` usa la tarifa **vigente a la fecha de llegada** y la guarda en el registro (`monto_por_km`). Cambiar la tarifa agrega una fila a `configuracion_depreciacion`; si ya existe una con la misma `vigente_desde` (segundo cambio el mismo día), se reemplaza (`ON CONFLICT (vigente_desde) DO UPDATE`) sin afectar a los registros ya calculados, que conservan su `monto_por_km`.
- `pago_chofer` se genera al consumir `LlegadaRegistrada`: `salario_base_chofer × 1.00` (viaje regular) o `× 1.15` (alquiler privado), y emite `PagoChoferRegistrado`.
- Solo el `ADMIN_SISTEMA` cambia la tarifa; el `ADMIN_SUCURSAL` registra gastos de taller de buses de **su** sucursal.
- Si falta el `bus_ref` al procesar un evento (llegó desordenado), se reintenta con espera creciente.

---

## 8. `bd_notificaciones` — Notificaciones

**Responsabilidad:** contactos, avisos de licencia programados y bitácora de correos. Solo consume eventos.

```sql
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
```

**Reglas que quedan en la aplicación:**
- Al consumir `ChoferRegistrado/Actualizado`: cancelar los recordatorios `PENDIENTE` del chofer cuya fecha de vencimiento cambió, y crear los 3 nuevos (`ON CONFLICT DO NOTHING`). No se crean los avisos cuya fecha ya pasó. Si `activo = false`, se cancelan todos.
- Un *scheduler* (`@Scheduled` diario) busca recordatorios `PENDIENTE` con `fecha_envio_programada <= hoy`, genera una `notificacion` para el chofer y otra por cada `ADMIN_SUCURSAL` activo de su sucursal (tabla `contacto`).
- `clave_dedup` evita enviar el mismo correo dos veces si el evento se reprocesa.
- `EnlaceContrasenaEmitido` y `ContrasenaCambiada` traen el `correo` en el payload (no dependen de `contacto`). Para `ENLACE_CONTRASENA` se guarda `cuerpo = '[contenido sensible no almacenado]'`; el enlace se arma con la variable `FRONTEND_URL`. Nunca se registran estos payloads en los logs.
- El envío falla → `intentos++` y `proximo_intento_en` con espera exponencial; tras N intentos queda `FALLIDA`. **El fallo del correo nunca revierte la operación de negocio.**

---

## 9. `bd_reportes` — Reportes (proyecciones de lectura)

**Responsabilidad:** tablas desnormalizadas, alimentadas **solo por eventos**, optimizadas para los 12 reportes. No tiene `FOREIGN KEY` (los eventos pueden llegar desordenados); las uniones se hacen por `id`.

```sql
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
```

**Cómo salen los reportes (resumen):**

| Reporte | Origen |
|---|---|
| Ingresos / gastos / ganancias (admin. sistema) | `rep_ingreso`, `rep_gasto`, `v_resumen_diario_sucursal` + `rep_sucursal ORDER BY nombre` |
| Rutas más demandadas | `rep_ingreso` (`VENTA_BOLETOS`) agrupado por `id_ruta`, suma de `cantidad_boletos`, unido a `rep_ruta` |
| Gastos de taller por categoría | `rep_gasto` (`TALLER`) agrupado por `nombre_categoria`, con filtro opcional por `id_sucursal` / `id_bus` |
| Mapa de rutas (Leaflet) | `rep_ruta` + `rep_sucursal` (latitud y longitud) filtrando por `id_sucursal_origen` |
| Listado de buses / choferes | `rep_bus`, `rep_chofer` + conteo de `rep_viaje` finalizados |
| Ingresos por boletos / alquiler | `rep_ingreso` filtrado por tipo, fecha, ruta o bus (vía `rep_viaje`) |
| Depreciación por bus | `rep_gasto` (`DEPRECIACION`) agrupado por `id_bus`: suma de `distancia_km` y de `monto` |
| Gastos de taller por bus y categoría | `rep_gasto` (`TALLER`) agrupado por `id_bus`, `nombre_categoria` |

**Reglas que quedan en la aplicación:**
- Cada consumidor usa `INSERT ... ON CONFLICT DO NOTHING` (o `DO UPDATE` en proyecciones de estado comparando `version`).
- `BoletosAnulados` y `AlquilerCancelado` → `UPDATE rep_ingreso SET anulado = true`.
- `RutaEliminada` → `eliminada = true` (se conserva para reportes históricos).
- `SalidaRegistrada` → `rep_bus.estado_operativo = 'EN_TRANSITO'`. `LlegadaRegistrada` → completar `rep_viaje`, actualizar `rep_bus.kilometraje_actual` y `rep_bus.estado_operativo = 'DISPONIBLE'`. Así el estado del bus en los listados se mantiene sin que Flota publique `BusActualizado` en cada viaje; `BusActualizado` (cambios del administrador) sigue mandando cuando su `version` es mayor.
- `TarifaDepreciacionCambiada` → *upsert* por `vigente_desde` (un segundo cambio el mismo día reemplaza al primero).
- **Supuesto:** anular una venta o un alquiler marca `anulado = true` sobre su fecha de pago original, así que el reporte del periodo original cambia de forma retroactiva.
- Los PDF se generan desde estas tablas (por ejemplo, JasperReports o OpenPDF).

---

## 10. Resumen

| Servicio | Base de datos | Tablas propias | Tablas `_ref` | Total |
|---|---|---|---|---|
| Identidad | `bd_identidad` | 3 | 0 | 3 + 2 comunes |
| Flota | `bd_flota` | 2 | 1 | 3 + 2 |
| Viajes | `bd_viajes` | 4 | 3 | 7 + 2 |
| Boletos | `bd_boletos` | 2 | 2 | 4 + 2 |
| Clientes y Cartera | `bd_clientes` | 3 | 0 | 3 + 2 |
| Alquileres | `bd_alquileres` | 3 | 2 | 5 + 2 |
| Costos | `bd_costos` | 7 | 2 | 9 + 2 |
| Notificaciones | `bd_notificaciones` | 4 | 0 (usa `contacto` y `chofer_licencia`, que son proyecciones) | 4 + 2 |
| Reportes | `bd_reportes` | 8 (+1 vista) | todas son proyecciones | 8 + 2 |

**Reglas del enunciado cubiertas por la propia base de datos:**
- Usuarios, buses y choferes no se eliminan (triggers) · siempre hay un administrador de sistema activo y cada sucursal conserva un administrador de sucursal activo (triggers con bloqueo, seguros ante concurrencia).
- Registros de salida y llegada inmutables (triggers) · tipo de viaje inmutable (trigger).
- Un asiento no se vende dos veces (índice único parcial) · un bus o chofer no se traslapa entre viajes (`EXCLUDE`).
- Pagos y reembolsos no se duplican (índice único por referencia) · saldo nunca negativo (`CHECK`).
- Estados válidos de viaje, compra, boleto y alquiler (`fn_validar_transicion_estado`) · asiento dentro de la capacidad del bus · total de taller = suma de líneas · gastos de taller y salarios inmutables.
- Una categoría con gastos no se elimina (`RESTRICT`) · la tarifa de depreciación histórica se conserva (instantánea en cada registro).