# Manual técnico — servicio-clientes (Clientes y Cartera)

Microservicio `CustomerWalletService` de TransportesGT. Mantiene el perfil comercial de **todos** los usuarios
(NIT, DPI, teléfono, dirección) y su cartera digital: recargas, cobro de compras de boletos y alquileres, y
reembolsos. Fuentes de verdad: `modelo-datos-postgresql-v4.md` §5 (tablas y reglas) y `catalogo-eventos-es-v4.md`
§2.5 y §5 (eventos y contrato de pagos).

| | |
|---|---|
| Stack | Java 21 · Spring Boot 4.1.1 · Spring Data JPA (Hibernate) · Flyway · Spring AMQP · Spring Security (resource server JWT) · springdoc |
| Librería de eventos | `com.github.luisbaquiax:EventosComunesTrasnportesGt:v2.1.1` (sobre, payloads, outbox, consumidor idempotente, topología) |
| Puerto / ruta base | `8085` / `/v1/customers` |
| Base de datos | PostgreSQL `bd_clientes` (usuario `svc_clientes`) |
| Broker | RabbitMQ, exchange topic `transporte.eventos`, DLX `transporte.eventos.dlx` |

---

## 1. Diagrama de tablas

```mermaid
erDiagram
    cliente ||--o{ cartera : "id_cliente"
    cartera ||--o{ movimiento_cartera : "id_cartera"

    outbox_evento {
        uuid id PK "= idEvento del sobre"
        varchar tipo_agregado
        uuid id_agregado
        varchar tipo_evento
        varchar llave_ruteo
        bigint version_agregado
        uuid id_correlacion
        jsonb datos
        timestamptz creado_en
        timestamptz publicado_en
        int intentos
    }
    evento_procesado {
        uuid id_evento PK
        varchar consumidor PK "nombre del listener"
        timestamptz procesado_en
    }
    cliente {
        uuid id PK
        uuid id_usuario UK "usuario de Identidad (cualquier rol)"
        varchar nombre_completo
        varchar correo
        varchar nit UK "se completa en el perfil; normalizado (mayúsculas, sin guiones)"
        varchar dpi UK
        varchar telefono
        varchar direccion
        boolean activo
        bigint version_usuario "versionAgregado del último UsuarioCreado/Actualizado aplicado"
        bigint version
        timestamptz creado_en
        timestamptz actualizado_en
    }
    cartera {
        uuid id PK
        uuid id_cliente FK,UK
        numeric saldo
        bigint version "bloqueo optimista contra pagos simultáneos"
        timestamptz actualizado_en
    }
    movimiento_cartera {
        uuid id PK "= idPago / idMovimiento"
        uuid id_cartera FK
        varchar tipo "RECARGA | PAGO | REEMBOLSO"
        numeric monto
        numeric saldo_posterior
        varchar tipo_referencia "BOLETOS | ALQUILER"
        uuid id_referencia "idCompra / idAlquiler"
        varchar clave_idempotencia UK "encabezado Clave-Idempotencia"
        date fecha_movimiento "elegida por el usuario"
        varchar descripcion
        timestamptz creado_en
    }
```

Restricciones que protegen el dinero en la propia base:

| Restricción | Garantiza |
|---|---|
| `uq_movimiento_referencia (tipo, tipo_referencia, id_referencia) WHERE tipo IN ('PAGO','REEMBOLSO')` | Un solo cobro y un solo reembolso por compra o alquiler |
| `movimiento_cartera_clave_idempotencia_key` | Una `Clave-Idempotencia` no se aplica dos veces |
| `cartera_saldo_check` / `saldo_posterior >= 0` | El saldo nunca queda negativo (respaldo del cálculo en Java) |
| `ck_mov_referencia` | Las recargas no tienen referencia; los pagos y reembolsos sí |
| `trg_movimiento_inmutable` (`fn_bloquear_modificacion`) | El libro de movimientos no se modifica ni se elimina |
| `uq_cliente_nit`, `uq_cliente_dpi`, `cliente_nit_check` | NIT y DPI únicos; NIT con formato válido y distinto de `CF` |

---

## 2. Diagrama de componentes

```mermaid
flowchart LR
    subgraph HTTP["controllers"]
        CLC[ClienteController<br/>/clientes]
        CAC[CarteraController<br/>/cartera · pagos internos]
        ADC[AdminController<br/>republicar eventos]
    end
    subgraph Seguridad["config/security"]
        FCI[FiltroClaveInterna<br/>solo GET /cartera/pagos]
    end
    subgraph Servicios["services (interfaz + impl)"]
        CS[ClienteService<br/>perfil · aplica UsuarioCreado/Actualizado]
        CAS[CarteraService<br/>saldo · recargas · movimientos]
        PS[PagoService<br/>cobros de la saga]
        RS[ReembolsoService]
        RES[RepublicacionEventosService]
        RC[ReglasCartera<br/>idempotencia · saldo nunca negativo]
    end
    subgraph Repos["repositories"]
        RCL[ClienteRepository]
        RCA[CarteraRepository]
        RMO[MovimientoCarteraRepository<br/>+ MovimientoCarteraSpecs]
    end
    subgraph Eventos["eventos"]
        MH[ManejadorUsuarioCreado/Actualizado<br/>ManejadorBoletosAnulados · ManejadorAlquilerCancelado]
        EV[EventosCartera]
        RO[[RegistroOutbox · DespachadorEventos<br/>librería]]
    end
    FCI --> CAC
    CLC --> CS
    CAC --> CAS & PS
    ADC --> RES
    CAS & PS & RS --> RC
    RO --> MH --> CS & RS
    CS & CAS & RS & RES --> EV --> RO
    CS & CAS & PS & RS & RES --> RCL & RCA & RMO
    RCL & RCA & RMO & RO --> DB[(bd_clientes)]
    BO[servicio-boletos] & AL[servicio-alquileres] -->|POST/GET /cartera/pagos| CAC
```

| Capa | Paquete | Responsabilidad |
|---|---|---|
| Web | `controllers` | Contrato HTTP, validación `@Valid`, códigos 200/201 y `Location` |
| Seguridad | `config.security` | Validar JWT, reglas por rol, `FiltroClaveInterna` |
| Negocio | `services.impl` | Orquestan cada operación: `CarteraService` (frontend), `PagoService` (saga de Boletos y Alquileres), `ReembolsoService`, `ClienteService` y `RepublicacionEventosService` (vuelve a encolar `PerfilClienteActualizado` de todos los clientes, solo ADMIN_SISTEMA). Las reglas de dinero están en el componente `ReglasCartera`: idempotencia, cuenta activa y cambio de saldo (`acreditar` / `debitar`: nunca negativo, 2 decimales) |
| Eventos | `eventos` | Manejadores (`ManejadorEvento<T>`) y armado de payloads hacia el outbox |
| Datos | `models`, `repositories` | Entidades JPA (`ddl-auto=validate`) y consultas, incluido `FOR UPDATE` |
| Errores | `exception` | Jerarquía `CustomerWalletExcpetion` y `RespuestaError` uniforme |

---

## 3. Saga de pago (compra de boletos en línea)

Boletos orquesta la saga; Cartera hace el cobro de forma síncrona e idempotente. El usuario sale del **JWT
reenviado**, y `Clave-Idempotencia = idCompra`.

```mermaid
sequenceDiagram
    autonumber
    participant B as Boletos
    participant C as Cartera
    participant DB as bd_clientes

    B->>C: POST /cartera/pagos (JWT del usuario, Clave-Idempotencia = idCompra)
    C->>C: Clave presente e igual a idReferencia (si no, 400)
    C->>DB: SELECT cartera … FOR UPDATE (por id_usuario)
    alt Sin cliente / inactivo / sin NIT
        C-->>B: 404 NO_ENCONTRADO / 403 CUENTA_INACTIVA / 422 PERFIL_INCOMPLETO
    else Ya existe el PAGO de esa referencia
        alt Misma cartera y mismo monto
            C-->>B: 200 { idPago, saldoPosterior } (el original)
        else Otra cartera u otro monto
            C-->>B: 409 CLAVE_IDEMPOTENCIA_REUTILIZADA
        end
    else Saldo < monto
        C-->>B: 422 SALDO_INSUFICIENTE
    else Saldo suficiente
        C->>DB: INSERT movimiento_cartera (PAGO, saldo_posterior)
        C->>DB: UPDATE cartera SET saldo = saldo_posterior
        C-->>B: 201 { idPago, saldoPosterior } + Location
    end
    Note over B,C: Si Boletos no recibe respuesta, su job consulta<br/>GET /cartera/pagos?tipoReferencia=BOLETOS&idReferencia={idCompra}<br/>con X-Clave-Interna (200 = hubo pago · 404 = no hubo)
```

Puntos de diseño:

- **El bloqueo va en la primera lectura.** Si la cartera se leyera antes sin bloqueo, Hibernate devolvería esa copia
  (con el saldo viejo) aunque después ejecutara el `FOR UPDATE`. Con el bloqueo inicial, un segundo pago simultáneo
  espera y ve el saldo ya descontado: responde 422 y no 409.
- **Un solo chequeo de idempotencia, con el bloqueo tomado.** Otro cobro de la misma referencia espera y luego ve el
  PAGO, así que responde 200 con el original. El índice `uq_movimiento_referencia` es el respaldo.
- **El pago no publica evento:** el ingreso lo publica Boletos (`VentaBoletosRegistrada`) al confirmar la compra.
- Alquileres sigue el mismo flujo con `tipoReferencia = ALQUILER` y `Clave-Idempotencia = idAlquiler`.

---

## 4. Reembolso (eliminación de un viaje)

```mermaid
sequenceDiagram
    autonumber
    participant MQ as RabbitMQ
    participant D as DespachadorEventos
    participant M as ManejadorBoletosAnulados<br/>/ ManejadorAlquilerCancelado
    participant R as ReembolsoService
    participant DB as bd_clientes

    MQ->>D: BoletosAnulados (cartera.boletos.q) / AlquilerCancelado (cartera.alquileres.q)
    D->>DB: BEGIN · INSERT evento_procesado ON CONFLICT DO NOTHING
    alt idEvento ya procesado
        D->>DB: COMMIT (se ignora el duplicado)
    else Evento nuevo
        D->>M: manejar(sobre)
        alt canal = MOSTRADOR / idPago = null
            M-->>D: no hay nada que reembolsar
        else Compra en línea o alquiler pagado con cartera
            M->>R: reembolsar(tipoReferencia, idReferencia, idPago, fecha en Guatemala)
            R->>DB: Busca el PAGO (por idPago, o por la referencia)
            alt No existe PAGO
                R-->>M: se ignora (no es error)
            else Existe PAGO
                R->>DB: SELECT cartera … FOR UPDATE
                alt Ya hay REEMBOLSO de esa referencia
                    R-->>M: no duplica
                else
                    R->>DB: INSERT REEMBOLSO (monto del PAGO) · UPDATE saldo
                    R->>DB: INSERT outbox_evento (CarteraReembolsada)
                end
            end
        end
        D->>DB: COMMIT (procesado + reembolso + evento, juntos)
    end
    Note over D,DB: Error transitorio → 3 reintentos (1, 2, 4 s) y luego DLQ.<br/>JSON inválido o dato imposible (EventoNoRecuperableException) → DLQ inmediato.
```

- El reembolso se aplica **aunque el cliente esté inactivo** (no pierde su dinero).
- `fecha_movimiento` = fecha en `America/Guatemala` de `canceladoEn` (alquiler) u `ocurridoEn` (boletos).
- Si `idPago` existe pero pertenece a otra compra o alquiler, el evento va a la DLQ: reembolsarlo devolvería el dinero
  equivocado.

---

## 5. Diagrama de despliegue

Producción en Google Cloud (proyecto `unimarketcunoc`). En local, `infra/docker-compose.yml` levanta lo mismo con
PostgreSQL y RabbitMQ en contenedores.

```mermaid
flowchart TB
    U([Navegador]) -->|HTTPS| FE[Frontend React<br/>Vercel · transporte-gt-01.vercel.app]
    FE -->|HTTPS + JWT| GW

    subgraph CR["Cloud Run · northamerica-south1"]
        GW[gateway-service<br/>Spring Cloud Gateway · público]
        SV[customers-service<br/>Java 21 · 1 vCPU · 2 GiB<br/>min 1 · max 1 instancia]
        BO[ticket-service]
    end

    subgraph Datos["Servicios administrados"]
        SQL[(Cloud SQL PostgreSQL · us-east1<br/>transportesgtdb · bd_clientes)]
        MQ{{CloudAMQP · RabbitMQ<br/>amqps:// · exchange transporte.eventos}}
    end

    subgraph CI["CI/CD"]
        GH[GitHub<br/>luisbaquiax/TransportesGT · main]
        CB[Cloud Build<br/>activador customers-service]
        AR[(Artifact Registry<br/>cloud-run-source-deploy/transportesgt/customers-service)]
    end

    GW -->|/v1/customers/**| SV
    SV -->|Cloud SQL socket factory| SQL
    SV <-->|eventos outbox / colas| MQ
    BO -->|POST/GET /cartera/pagos| SV
    AL[private-rental-service<br/>europe-west1] -->|POST/GET /cartera/pagos| SV
    GH -->|push / merge a main| CB
    CB -->|docker build CustomerWalletService/| AR
    CB -->|gcloud run services update| SV
    AR -.->|imagen :COMMIT_SHA| SV
```

| Elemento | Detalle |
|---|---|
| Imagen | `Dockerfile` en dos etapas: `maven:3.9-eclipse-temurin-21` compila (`./mvnw clean package`) y `bellsoft/liberica-openjdk-alpine:21` ejecuta `app.jar` |
| CI/CD | Cada merge a `main` dispara el activador de Cloud Build: `docker build CustomerWalletService -f CustomerWalletService/Dockerfile`, push a Artifact Registry con la etiqueta del commit y `gcloud run services update customers-service` |
| Base de datos | Cloud SQL `unimarketcunoc:us-east1:transportesgtdb`, base `bd_clientes`, por la *socket factory* (`jdbc:postgresql:///bd_clientes?cloudSqlInstance=…&socketFactory=…`) |
| Configuración | Variables de entorno: `SPRING_PROFILES_DEFAULT=prod`, `SPRING_DATASOURCE_*`, `RABBITMQ_URL`, `JWT_SECRET` (el mismo de todos los servicios), `CLAVE_INTERNA`, `JWT_ISSUER`, `SERVER_PORT=8080`, `SWAGGER_HABILITADO`, `DB_POOL_MAX` |
| Esquema | Flyway aplica las migraciones al arrancar; Hibernate solo valida. En la nube no se modifican migraciones ya aplicadas: los cambios van en una versión nueva |
| Instancias | Exactamente 1 (`min` y `max`). Varias serían seguras: el outbox y `evento_procesado` hacen idempotente el consumo |
| CPU | Hoy con CPU solo durante peticiones (valor por defecto). Para que los consumidores procesen eventos sin tráfico HTTP conviene `--no-cpu-throttling` |
| Llamadas | Recibe los cobros de Boletos y Alquileres. `GET /cartera/pagos` acepta `X-Clave-Interna` para los jobs de recuperación |
| Acceso | Público (`ingress=all`, sin IAM de invocación); los datos los protege el JWT. Pendiente: dejar público solo el gateway |
| Cuenta de servicio | La de Compute por defecto (`…-compute@developer.gserviceaccount.com`), compartida con los demás servicios |
| Salud | `/v1/customers/actuator/health` (`liveness` y `readiness`) |
