# Manual técnico — servicio-costos (Costos operativos)

Microservicio `OperationalCostsService` de TransportesGT. Registra los costos de operación:
- tarifa de depreciación por kilómetro, con historial por `vigente_desde`;
- catálogo de categorías de gasto;
- gastos de taller **inmutables** con sus líneas (repuestos y mano de obra);
- costos de cada viaje (combustible, depreciación y salario del chofer), calculados al consumir `LlegadaRegistrada`.

Fuentes de verdad: `modelo-datos-postgresql-v4.md` (tablas de Costos), `catalogo-eventos-es-v4.md` (eventos de
Costos) y el README del servicio.

| | |
|---|---|
| Stack | Java 21 · Spring Boot 4.1.1 · Spring Data JPA (Hibernate) · Flyway · Spring AMQP · Spring Security (resource server JWT) · MapStruct · springdoc |
| Librería de eventos | `com.github.luisbaquiax:EventosComunesTrasnportesGt` (sobre, payloads, outbox, consumidor idempotente) |
| Puerto / ruta base | `8087` / `/v1/costs` |
| Base de datos | PostgreSQL `bd_costos` |
| Broker | RabbitMQ, exchange topic `transporte.eventos`, DLX `transporte.eventos.dlx` |

---

## 1. Diagrama de tablas

Generado de `V1__esquema_inicial.sql`. `sucursal_ref` y `bus_ref` son proyecciones de Identidad y Flota. `gasto_taller` y sus líneas no se modifican: un trigger diferido exige que el total sea la suma de las líneas.

```mermaid
erDiagram
    sucursal_ref ||--o{ bus_ref : "id_sucursal"
    bus_ref ||--o{ gasto_taller : "id_bus"
    sucursal_ref ||--o{ gasto_taller : "id_sucursal"
    gasto_taller ||--o{ gasto_taller_linea : "id_gasto_taller"
    categoria_gasto ||--o{ gasto_taller_linea : "id_categoria"
    bus_ref ||--o{ gasto_combustible : "id_bus"
    sucursal_ref ||--o{ gasto_combustible : "id_sucursal"
    bus_ref ||--o{ registro_depreciacion : "id_bus"
    sucursal_ref ||--o{ registro_depreciacion : "id_sucursal"
    sucursal_ref ||--o{ pago_chofer : "id_sucursal"

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
    sucursal_ref {
        uuid id PK
        varchar nombre
        boolean activa
        bigint version
    }
    bus_ref {
        uuid id PK
        uuid id_sucursal FK
        varchar placa
        boolean activo
        bigint version
    }
    configuracion_depreciacion {
        uuid id PK
        numeric monto_por_km
        date vigente_desde UK
        uuid creado_por "administrador de sistema"
        timestamptz creado_en
    }
    categoria_gasto {
        uuid id PK
        varchar nombre UK
        varchar descripcion
        boolean activa
        timestamptz creado_en
    }
    gasto_taller {
        uuid id PK
        uuid id_bus FK
        uuid id_sucursal FK
        date fecha_servicio "elegida por el usuario"
        numeric total
        varchar observaciones
        uuid registrado_por
        timestamptz creado_en
    }
    gasto_taller_linea {
        uuid id PK
        uuid id_gasto_taller FK
        uuid id_categoria FK "obligatoria"
        varchar descripcion
        varchar tipo "MANO_DE_OBRA | REPUESTO"
        numeric monto
    }
    gasto_combustible {
        uuid id PK
        uuid id_viaje UK
        uuid id_bus FK
        uuid id_sucursal FK
        numeric monto
        date fecha_gasto
        timestamptz creado_en
    }
    registro_depreciacion {
        uuid id PK
        uuid id_viaje UK
        uuid id_bus FK
        uuid id_sucursal FK
        numeric distancia_km
        numeric monto_por_km "tarifa usada (instantánea)"
        numeric monto
        date fecha_calculo
        timestamptz creado_en
    }
    pago_chofer {
        uuid id PK
        uuid id_viaje UK
        uuid id_chofer
        uuid id_sucursal FK
        numeric salario_base
        numeric multiplicador "1.15 en alquiler privado"
        numeric monto
        date fecha_pago
        timestamptz creado_en
    }
```

## 2. Diagrama de componentes

```mermaid
flowchart LR
    subgraph HTTP["controllers"]
        CC[CostosController<br/>tarifas · categorías · taller · resumen de viaje]
    end
    subgraph Seguridad["config/security"]
        PAS[PoliticaAccesoSucursal<br/>SUC solo su sucursal]
    end
    subgraph Servicios["services (interfaz + impl)"]
        TDS[TarifaDepreciacionService]
        CGS[CategoriaGastoService]
        GTS[GastoTallerService]
        RVS[ResumenViajeService]
    end
    subgraph Dominio["dominio"]
        CCV[CalculadoraCostosViaje<br/>combustible · depreciación · salario]
        PPC[PoliticaPagoChofer<br/>1.00 regular · 1.15 alquiler]
    end
    subgraph Repos["repositories"]
        RTA[ConfiguracionDepreciacionRepository]
        RCA[CategoriaGastoRepository]
        RGT[GastoTallerRepository · GastoTallerLineaRepository<br/>+ GastoTallerEspecificaciones]
        RVI[GastoCombustible · RegistroDepreciacion · PagoChofer<br/>Repository]
        RREF[SucursalRefRepository · BusRefRepository]
    end
    subgraph Eventos["eventos"]
        ML[ManejadorLlegadaRegistrada<br/>idempotente por idViaje]
        MP[ManejadorSucursal* · ManejadorBus*<br/>proyecciones]
        EV[EventosCostos]
        RO[[RegistroOutbox · DespachadorEventos<br/>librería]]
    end
    CC --> TDS & CGS & GTS & RVS
    TDS & GTS & RVS --> PAS
    RO --> ML & MP
    ML --> CCV --> PPC
    CCV --> RTA
    ML & TDS & GTS --> EV --> RO
    TDS --> RTA
    CGS --> RCA
    GTS --> RCA & RGT & RREF
    RVS & ML --> RVI
    MP --> RREF
    RTA & RCA & RGT & RVI & RREF & RO --> DB[(bd_costos)]
```

- Un servicio por subdominio (`TarifaDepreciacionService`, `CategoriaGastoService`, `GastoTallerService`,
  `ResumenViajeService`) con su implementación en `impl/`; el controlador inyecta cada uno.
- Las fórmulas viven en `dominio/`: `CalculadoraCostosViaje` y `PoliticaPagoChofer` (multiplicadores 1.00 y 1.15).
- `PoliticaAccesoSucursal` aplica la regla de sucursal: el administrador de sucursal solo ve y registra lo suyo.

## 3. Costos de un viaje

```mermaid
sequenceDiagram
    participant V as Viajes
    participant MQ as RabbitMQ
    participant C as Costos
    participant DB as bd_costos
    V--)MQ: LlegadaRegistrada (km recorridos, combustible, salario base, tipo de viaje)
    MQ->>C: ManejadorLlegadaRegistrada
    C->>DB: ¿existe gasto_combustible del viaje? (idempotencia)
    alt primera vez
        C->>C: combustible = total del evento<br/>depreciación = km × tarifa vigente a la fecha de llegada<br/>salario = base × 1.00 (regular) o 1.15 (alquiler)
        C->>DB: BEGIN · gasto_combustible · registro_depreciacion · pago_chofer · outbox · COMMIT
        C--)MQ: GastoCombustibleRegistrado · DepreciacionCalculada · PagoChoferRegistrado
    else ya procesado
        C->>C: ignora el evento
    end
```

Si no hay tarifa vigente a la fecha de llegada, el evento falla con `SIN_TARIFA` y se reintenta; cambiar la tarifa no
altera los costos ya calculados.

## 4. Diagrama de despliegue

Producción en Google Cloud (proyecto `unimarketcunoc`). En local, `infra/docker-compose.yml` levanta lo mismo con
PostgreSQL y RabbitMQ en contenedores.

```mermaid
flowchart TB
    U([Navegador]) -->|HTTPS| FE[Frontend React<br/>Vercel · transporte-gt-01.vercel.app]
    FE -->|HTTPS + JWT| GW

    subgraph CR["Cloud Run · northamerica-south1"]
        GW[gateway-service<br/>Spring Cloud Gateway · público]
        SV[operation-service<br/>Java 21 · 1 vCPU · 1 GiB<br/>min 1 · max 1 instancia]

    end

    subgraph Datos["Servicios administrados"]
        SQL[(Cloud SQL PostgreSQL · us-east1<br/>transportesgtdb · bd_costos)]
        MQ{{CloudAMQP · RabbitMQ<br/>amqps:// · exchange transporte.eventos}}
    end

    subgraph CI["CI/CD"]
        GH[GitHub<br/>luisbaquiax/TransportesGT · main]
        CB[Cloud Build<br/>activador operation-service]
        AR[(Artifact Registry<br/>cloud-run-source-deploy/transportesgt/operation-service)]
    end

    GW -->|/v1/costs/**| SV
    SV -->|Cloud SQL socket factory| SQL
    SV <-->|eventos outbox / colas| MQ

    GH -->|push / merge a main| CB
    CB -->|docker build OperationalCostsService/| AR
    CB -->|gcloud run services update| SV
    AR -.->|imagen :COMMIT_SHA| SV
```

| Elemento | Detalle |
|---|---|
| Imagen | `Dockerfile` en dos etapas: `maven:3.9-eclipse-temurin-21` compila (`./mvnw clean package`) y `bellsoft/liberica-openjdk-alpine:21` ejecuta `app.jar` |
| CI/CD | Cada merge a `main` dispara el activador de Cloud Build: `docker build OperationalCostsService -f OperationalCostsService/Dockerfile`, push a Artifact Registry con la etiqueta del commit y `gcloud run services update operation-service` |
| Base de datos | Cloud SQL `unimarketcunoc:us-east1:transportesgtdb`, base `bd_costos`, por la *socket factory* (`jdbc:postgresql:///bd_costos?cloudSqlInstance=…&socketFactory=…`) |
| Configuración | Variables de entorno: `SPRING_PROFILES_DEFAULT=prod`, `SPRING_DATASOURCE_*`, `RABBITMQ_URL`, `JWT_SECRET` (el mismo de todos los servicios), `SERVER_PORT=8080`, `SWAGGER_HABILITADO`, `DB_POOL_MAX` |
| Esquema | Flyway aplica las migraciones al arrancar; Hibernate solo valida. En la nube no se modifican migraciones ya aplicadas: los cambios van en una versión nueva |
| Instancias | Exactamente 1 (`min` y `max`). Varias serían seguras: el outbox y `evento_procesado` hacen idempotente el consumo |
| CPU | Hoy con CPU solo durante peticiones (valor por defecto). Para que los consumidores procesen eventos sin tráfico HTTP conviene `--no-cpu-throttling` |
| Eventos | Consume `SucursalCreada/Actualizada`, `BusRegistrado/Actualizado` y `LlegadaRegistrada`; publica `GastoCombustibleRegistrado`, `DepreciacionCalculada`, `PagoChoferRegistrado`, `GastoTallerRegistrado` y `TarifaDepreciacionCambiada`. No llama a otros servicios |
| Acceso | Público (`ingress=all`, sin IAM de invocación); los datos los protege el JWT. Pendiente: dejar público solo el gateway |
| Cuenta de servicio | La de Compute por defecto (`…-compute@developer.gserviceaccount.com`), compartida con los demás servicios |
| Salud | `/v1/costs/actuator/health` (`liveness` y `readiness`) |
