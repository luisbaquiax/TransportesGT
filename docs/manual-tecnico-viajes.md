# Manual técnico — servicio-viajes (Viajes)

Microservicio `TripService` de TransportesGT. Es la fuente de verdad de las **rutas** y los **viajes**:
- rutas entre sucursales (las define el administrador de la sucursal de origen);
- viajes regulares programados con bus, chofer y horario;
- viajes de alquiler, que nacen al confirmarse un alquiler y esperan bus y chofer;
- registros **inmutables** de salida y llegada, con el kilometraje y el combustible.

Fuentes de verdad: `modelo-datos-postgresql-v4.md` (tablas de Viajes), `catalogo-eventos-es-v4.md` (eventos de
Viajes) y `plan-de-trabajo.md` (endpoints de rutas y viajes).

| | |
|---|---|
| Stack | Java 21 · Spring Boot 4.1.1 · Spring Data JPA (Hibernate) · Flyway · Spring AMQP · Spring Security (resource server JWT) · RestClient · MapStruct · springdoc |
| Librería de eventos | `com.github.luisbaquiax:EventosComunesTrasnportesGt` (sobre, payloads, outbox, consumidor idempotente) |
| Puerto / ruta base | `8083` / `/v1/trips` |
| Base de datos | PostgreSQL `bd_viajes` |
| Broker | RabbitMQ, exchange topic `transporte.eventos`, DLX `transporte.eventos.dlx` |

---

## 1. Diagrama de tablas

Generado de `V1__esquema_inicial.sql`. `sucursal_ref`, `bus_ref` y `chofer_ref` son proyecciones de Identidad y Flota; `registro_salida` y `registro_llegada` son inmutables (un trigger bloquea UPDATE y DELETE).

```mermaid
erDiagram
    sucursal_ref ||--o{ ruta : "id_sucursal_origen"
    sucursal_ref ||--o{ ruta : "id_sucursal_destino"
    ruta ||--o{ viaje : "id_ruta"
    sucursal_ref ||--o{ viaje : "id_sucursal_origen"
    bus_ref ||--o{ viaje : "id_bus"
    chofer_ref ||--o{ viaje : "id_chofer"
    viaje ||--o{ registro_salida : "id_viaje"
    viaje ||--o{ registro_llegada : "id_viaje"

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
        uuid id_sucursal
        varchar placa
        smallint capacidad_pasajeros
        varchar estado_operativo "viene en BusActualizado"
        boolean activo
        bigint version
    }
    chofer_ref {
        uuid id PK
        uuid id_usuario UK "usuario con rol CHOFER (solo registra salida y llegada)"
        uuid id_sucursal
        varchar nombre_completo
        numeric salario_base_viaje
        date fecha_vencimiento_licencia "para no asignar choferes con licencia vencida"
        boolean activo
        bigint version
    }
    ruta {
        uuid id PK
        uuid id_sucursal_origen FK
        uuid id_sucursal_destino FK
        numeric distancia_km
        numeric precio_boleto
        uuid creado_por "administrador de la sucursal de origen"
        bigint version
        timestamptz creado_en
        timestamptz actualizado_en
    }
    viaje {
        uuid id PK
        varchar tipo_viaje "REGULAR | ALQUILER_PRIVADO"
        varchar estado "PROGRAMADO | PENDIENTE_ASIGNACION | EN_TRANSITO | FINALIZADO"
        uuid id_ruta FK "solo REGULAR"
        uuid id_alquiler UK "solo ALQUILER_PRIVADO"
        uuid id_sucursal_origen FK
        varchar nombre_origen
        varchar nombre_destino
        uuid id_bus FK
        uuid id_chofer FK
        timestamptz fecha_hora_salida
        timestamptz fecha_hora_llegada_estimada
        numeric precio_boleto "instantánea del precio de la ruta"
        smallint numero_pasajeros "solo ALQUILER_PRIVADO (viene en AlquilerConfirmado)"
        bigint version
        uuid creado_por
        timestamptz creado_en
        timestamptz actualizado_en
        with id_bus FK
        with id_chofer FK
    }
    registro_salida {
        uuid id PK
        uuid id_viaje FK,UK
        uuid id_bus
        uuid id_chofer
        timestamptz fecha_hora_salida_real
        numeric kilometraje_inicial
        uuid registrado_por "administrador de sucursal o chofer"
        timestamptz registrado_en
    }
    registro_llegada {
        uuid id PK
        uuid id_viaje FK,UK
        timestamptz fecha_hora_llegada_real
        numeric kilometraje_final
        numeric distancia_recorrida_km
        numeric gasto_combustible_total
        numeric salario_base_chofer "instantánea al momento de la llegada"
        uuid registrado_por
        timestamptz registrado_en
    }
```

## 2. Diagrama de componentes

```mermaid
flowchart LR
    subgraph HTTP["controllers"]
        VC[ViajeController<br/>/viajes · salida · llegada · asignación]
        RC[RutaController<br/>/rutas]
        AC[AdminController<br/>republicar eventos]
    end
    subgraph Servicios["services (interfaz + impl)"]
        PV[viaje<br/>ProgramacionViajesService<br/>+ ValidadorRecursos · GuardadoViaje]
        RV[viaje<br/>RegistroViajeService<br/>salida y llegada inmutables]
        CQ[consulta<br/>ConsultaViajesService]
        RS[ruta<br/>RutaService<br/>+ ValidadorRuta]
        PR[proyeccion<br/>ProyeccionReferenciasService<br/>ViajesAlquilerService]
        ADM[admin<br/>RepublicacionEventosService]
        COM[comun · autorizacion<br/>BuscadorViajes · AutorizacionViaje]
    end
    subgraph Clientes["clients"]
        BCL[BoletosClient<br/>RestClient]
    end
    subgraph Repos["repositories"]
        RVI[ViajeRepository<br/>+ ViajeSpecs]
        RRU[RutaRepository]
        RRE[RegistroSalidaRepository<br/>RegistroLlegadaRepository]
        RREF[SucursalRef · BusRef · ChoferRef<br/>Repository]
    end
    subgraph Eventos["eventos"]
        MH[Manejador Sucursal* · Bus* · Chofer*<br/>ManejadorAlquilerConfirmado]
        EV[EventosViaje<br/>+ MapeadorEventosViaje · RecursosEvento]
        RO[[RegistroOutbox · DespachadorEventos<br/>librería]]
    end
    VC --> PV & RV & CQ
    RC --> RS
    AC --> ADM
    PV --> BCL
    PV & RV & CQ --> COM
    RO --> MH --> PR
    PV & RV & RS & PR & ADM --> EV --> RO
    COM & PV & RV & CQ & RS & PR & ADM --> RVI & RRU & RRE & RREF
    RVI & RRU & RRE & RREF & RO --> DB[(bd_viajes)]
    BCL -->|GET asiento-maximo| BO[servicio-boletos]
```

- Una interfaz por caso de uso y su implementación en `impl/`; los controladores dependen solo de interfaces.
- `ProgramacionViajesService` crea, edita, asigna y elimina viajes; `RegistroViajeService` registra la salida y la
  llegada. Las validaciones de bus y chofer (activos, de la sucursal, licencia vigente, sin traslapes) están en
  `ValidadorRecursos`.
- Los eventos se guardan en el outbox en la misma transacción que el cambio (`EventosViaje`).

## 3. Ciclo de vida de un viaje

```mermaid
stateDiagram-v2
    [*] --> PROGRAMADO: POST /viajes (regular)
    [*] --> PENDIENTE_ASIGNACION: AlquilerConfirmado
    PENDIENTE_ASIGNACION --> PROGRAMADO: PATCH /viajes/{id}/asignacion
    PROGRAMADO --> EN_TRANSITO: POST /viajes/{id}/salida
    EN_TRANSITO --> FINALIZADO: POST /viajes/{id}/llegada
    PROGRAMADO --> [*]: DELETE (ViajeEliminado)
    PENDIENTE_ASIGNACION --> [*]: DELETE (ViajeEliminado)
    FINALIZADO --> [*]
```

- Un viaje `EN_TRANSITO` o `FINALIZADO` no se edita ni se elimina (`VIAJE_NO_ELIMINABLE`).
- Al cambiar el bus de un viaje con boletos, consulta a Boletos `asiento-maximo` y rechaza un bus más pequeño.
- La salida y la llegada las registra el administrador de la sucursal o el chofer **de su propio viaje**
  (`AutorizacionViaje` compara el `idUsuario` del token con el `id_usuario` de `chofer_ref`).

## 4. Salida y llegada

```mermaid
sequenceDiagram
    actor U as Admin de sucursal o chofer
    participant V as Viajes
    participant DB as bd_viajes
    participant MQ as RabbitMQ
    U->>V: POST /viajes/{id}/salida (hora real, kilometraje inicial)
    V->>DB: BEGIN · registro_salida · viaje = EN_TRANSITO · outbox · COMMIT
    V--)MQ: SalidaRegistrada · ViajeActualizado
    Note over MQ: Flota marca el bus EN_TRANSITO
    U->>V: POST /viajes/{id}/llegada (hora real, kilometraje final, combustible)
    V->>DB: BEGIN · registro_llegada · viaje = FINALIZADO · outbox · COMMIT
    V--)MQ: LlegadaRegistrada · ViajeActualizado
    Note over MQ: Flota actualiza el kilometraje · Costos calcula combustible,<br/>depreciación y salario · Reportes y Alquileres actualizan sus proyecciones
```

## 5. Diagrama de despliegue

Producción en Google Cloud (proyecto `unimarketcunoc`). En local, `infra/docker-compose.yml` levanta lo mismo con
PostgreSQL y RabbitMQ en contenedores.

```mermaid
flowchart TB
    U([Navegador]) -->|HTTPS| FE[Frontend React<br/>Vercel · transporte-gt-01.vercel.app]
    FE -->|HTTPS + JWT| GW

    subgraph CR["Cloud Run · northamerica-south1"]
        GW[gateway-service<br/>Spring Cloud Gateway · público]
        SV[trip-service<br/>Java 21 · 1 vCPU · 2 GiB<br/>min 1 · max 1 instancia]
        BO[ticket-service]
        FL[fleet-service]
    end

    subgraph Datos["Servicios administrados"]
        SQL[(Cloud SQL PostgreSQL · us-east1<br/>transportesgtdb · bd_viajes)]
        MQ{{CloudAMQP · RabbitMQ<br/>amqps:// · exchange transporte.eventos}}
    end

    subgraph CI["CI/CD"]
        GH[GitHub<br/>luisbaquiax/TransportesGT · main]
        CB[Cloud Build<br/>activador trip-service]
        AR[(Artifact Registry<br/>cloud-run-source-deploy/transportesgt/trip-service)]
    end

    GW -->|/v1/trips/**| SV
    SV -->|Cloud SQL socket factory| SQL
    SV <-->|eventos outbox / colas| MQ
    SV -->|GET asiento-maximo| BO
    FL -->|GET /viajes/activos · X-Clave-Interna| SV
    GH -->|push / merge a main| CB
    CB -->|docker build TripService/| AR
    CB -->|gcloud run services update| SV
    AR -.->|imagen :COMMIT_SHA| SV
```

| Elemento | Detalle |
|---|---|
| Imagen | `Dockerfile` en dos etapas: `maven:3.9-eclipse-temurin-21` compila (`./mvnw clean package`) y `bellsoft/liberica-openjdk-alpine:21` ejecuta `app.jar` |
| CI/CD | Cada merge a `main` dispara el activador de Cloud Build: `docker build TripService -f TripService/Dockerfile`, push a Artifact Registry con la etiqueta del commit y `gcloud run services update trip-service` |
| Base de datos | Cloud SQL `unimarketcunoc:us-east1:transportesgtdb`, base `bd_viajes`, por la *socket factory* (`jdbc:postgresql:///bd_viajes?cloudSqlInstance=…&socketFactory=…`) |
| Configuración | Variables de entorno: `SPRING_PROFILES_DEFAULT=prod`, `SPRING_DATASOURCE_*`, `RABBITMQ_URL`, `JWT_SECRET` (el mismo de todos los servicios), `BOLETOS_URL`, `CLAVE_INTERNA`, `SERVER_PORT=8080`, `SWAGGER_HABILITADO`, `DB_POOL_MAX` |
| Esquema | Flyway aplica las migraciones al arrancar; Hibernate solo valida. En la nube no se modifican migraciones ya aplicadas: los cambios van en una versión nueva |
| Instancias | Exactamente 1 (`min` y `max`). Varias serían seguras: el outbox y `evento_procesado` hacen idempotente el consumo |
| CPU | Hoy con CPU solo durante peticiones (valor por defecto). Para que los consumidores procesen eventos sin tráfico HTTP conviene `--no-cpu-throttling` |
| Llamadas | Consulta a Boletos `asiento-maximo` antes de cambiar el bus de un viaje; Flota le consulta `/viajes/activos` con `X-Clave-Interna` |
| Acceso | Público (`ingress=all`, sin IAM de invocación); los datos los protege el JWT. Pendiente: dejar público solo el gateway |
| Cuenta de servicio | La de Compute por defecto (`…-compute@developer.gserviceaccount.com`), compartida con los demás servicios |
| Salud | `/v1/trips/actuator/health` (`liveness` y `readiness`) |
