# Manual técnico — servicio-boletos (Boletos)

Microservicio `TicketService` de TransportesGT. Vende boletos de los viajes regulares: en línea, pagados con la
cartera del cliente (saga con Cartera), y en mostrador, por un cajero o administrador de sucursal. Fuentes de
verdad: `modelo-datos-postgresql-v4.md` §6 (tablas y reglas) y `catalogo-eventos-es-v4.md` (eventos de Viajes,
Cartera y Boletos, y el contrato de pagos §5).

| | |
|---|---|
| Stack | Java 21 · Spring Boot 4.1.1 · Spring Data JPA (Hibernate) · Flyway · Spring AMQP · Spring Security (resource server JWT) · RestClient + Resilience4j · springdoc |
| Librería de eventos | `com.github.luisbaquiax:EventosComunesTrasnportesGt:v2.1.1` (sobre, payloads, outbox, consumidor idempotente, topología) |
| Puerto / ruta base | `8084` / `/v1/tickets` |
| Base de datos | PostgreSQL `bd_boletos` |
| Broker | RabbitMQ, exchange topic `transporte.eventos`, DLX `transporte.eventos.dlx` |

Tablas: `viaje_ref` y `cliente_ref` (proyecciones), `compra` y `boleto` (propias), `outbox_evento` y
`evento_procesado` (librería). El índice parcial `uq_asiento_activo` y los triggers `trg_boleto_validar_asiento`,
`trg_compra_transicion` y `trg_boleto_transicion` están en `V1__esquema_inicial.sql`.

---

## 1. Diagrama de tablas

Generado de `V1__esquema_inicial.sql`. `viaje_ref` y `cliente_ref` son proyecciones de Viajes y Cartera; el índice parcial `uq_asiento_activo (id_viaje, numero_asiento)` impide vender dos veces un asiento `RESERVADO` o `CONFIRMADO`.

```mermaid
erDiagram
    viaje_ref ||--o{ compra : "id_viaje"
    compra ||--o{ boleto : "id_compra"
    viaje_ref ||--o{ boleto : "id_viaje"

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
    viaje_ref {
        uuid id PK
        uuid id_ruta
        uuid id_sucursal_origen
        varchar nombre_origen
        varchar nombre_destino
        timestamptz fecha_hora_salida
        timestamptz fecha_hora_llegada_estimada
        numeric precio_boleto
        uuid id_bus
        varchar placa_bus
        smallint capacidad_bus
        varchar estado
        boolean eliminado
        bigint version
    }
    cliente_ref {
        uuid id_usuario PK
        varchar nit "normalizado; null mientras el perfil no tenga NIT"
        varchar nombre_completo
        boolean activo
        bigint version
    }
    compra {
        uuid id PK "idCompra, generado por el cliente/servicio (idempotencia)"
        uuid id_viaje FK
        varchar canal "EN_LINEA | MOSTRADOR"
        varchar estado "RESERVADA | CONFIRMADA | ANULADA"
        smallint cantidad_boletos
        numeric precio_unitario
        numeric monto_total
        uuid id_usuario_comprador "null en venta presencial sin cuenta"
        varchar nit_comprador
        varchar nombre_comprador
        uuid id_pago "movimiento de cartera (solo EN_LINEA)"
        date fecha_pago "elegida por el usuario"
        uuid id_sucursal_ingreso
        uuid id_usuario_vendedor "cajero / administrador (MOSTRADOR)"
        timestamptz reserva_expira_en "para liberar reservas sin pago"
        varchar motivo_anulacion
        bigint version
        timestamptz creado_en
        timestamptz confirmado_en
        timestamptz anulado_en
    }
    boleto {
        uuid id PK
        uuid id_compra FK
        uuid id_viaje FK
        smallint numero_asiento
        varchar estado "RESERVADO | CONFIRMADO | ANULADO"
        varchar codigo UK
        timestamptz creado_en
        timestamptz abordado_en
        uuid abordado_por
    }
```

## 2. Diagrama de componentes

```mermaid
flowchart LR
    subgraph HTTP["controllers"]
        VC[ViajeController<br/>viajes a la venta · asientos]
        CC[CompraController<br/>compras · historial · PDF]
        MC[VentaMostradorController]
        AB[AbordajeController<br/>pasajeros · abordajes]
    end
    subgraph Servicios["services (interfaz + impl)"]
        CEL[compra<br/>CompraEnLineaService<br/>orquesta la saga]
        VMS[compra<br/>VentaMostradorService]
        GC[GestorCompras<br/>pasos transaccionales<br/>+ ValidadorCompra · GeneradorCodigoBoleto]
        REC[compra<br/>RecuperacionComprasService<br/>AnulacionViajeService]
        CQ[consulta<br/>ConsultaViajesService · ConsultaComprasService<br/>+ ReglasVisibilidad]
        PDF[consulta<br/>BoletosPdfService]
        ABS[abordaje<br/>AbordajeService · PasajerosViajeService<br/>+ ValidadorAbordaje]
        PR[proyeccion<br/>ProyeccionViajesService<br/>ProyeccionClientesService]
    end
    subgraph Documentos["documentos"]
        BP[BoletosPdf<br/>OpenPDF]
        QR[GeneradorQr<br/>ZXing]
    end
    subgraph Pago["clients · puerto de pago"]
        PP[[PasarelaPago]]
        CPP[CarteraPasarelaPago<br/>Retry + CircuitBreaker]
        CL[CarteraClient<br/>RestClient · timeout 3 s]
    end
    subgraph Repos["repositories"]
        RCO[CompraRepository<br/>+ CompraSpecs]
        RBO[BoletoRepository]
        RVR[ViajeRefRepository<br/>ClienteRefRepository]
    end
    subgraph Eventos["eventos"]
        MH[ManejadorViajeProgramado/Actualizado/Eliminado<br/>ManejadorPerfilClienteActualizado]
        EV[EventosBoletos]
        RO[[RegistroOutbox · DespachadorEventos<br/>librería]]
    end
    JOB[jobs/RecuperacionComprasJob<br/>cada 60 s]
    VC --> CQ
    CC --> CEL & CQ & PDF
    MC --> VMS
    AB --> ABS
    JOB --> REC
    CEL & VMS & REC --> GC
    CEL & REC --> PP
    PP -.-> CPP --> CL
    PDF --> BP --> QR
    RO --> MH --> PR & REC
    GC --> EV --> RO
    GC & CQ & PDF & ABS & PR & REC --> RCO & RBO & RVR
    RCO & RBO & RVR & RO --> DB[(bd_boletos)]
    CL -->|POST/GET /cartera/pagos| CA[servicio-clientes]
```

| Capa | Paquete | Responsabilidad |
|---|---|---|
| Web | `controllers` | Contrato HTTP, validación `@Valid`, códigos 200/201/202 y `Location` |
| Seguridad | `config.security` | Validar JWT, reglas por rol, `FiltroClaveInterna` |
| Negocio | `services.compra`, `services.consulta`, `services.abordaje`, `services.proyeccion` (cada uno con su `impl`) | Interfaces y su implementación, agrupadas por tema. Cada servicio vive junto a los componentes package-private que usa, para que ninguno tenga que ser público. **compra**: todo lo que cambia el estado de una compra (en línea, mostrador, recuperación y anulación por viaje); `CompraEnLineaService` orquesta la saga **sin** transacción, `GestorCompras` (bean aparte, para que el proxy de `@Transactional` aplique) hace cada paso transaccional, y `ValidadorCompra` y `GeneradorCodigoBoleto` aplican las reglas. **consulta**: viajes a la venta, mapa de asientos, historial y el PDF de los boletos, con `ReglasVisibilidad`. **abordaje**: lista de pasajeros y registro del abordaje, con `ValidadorAbordaje` (sucursal de origen, viaje `PROGRAMADO`, boleto `CONFIRMADO` del viaje). **proyeccion**: *upsert* de `viaje_ref` y `cliente_ref` desde los eventos de Viajes y Cartera (no publica eventos) |
| Integración | `clients` | `PasarelaPago` (puerto) y su adaptador `CarteraPasarelaPago`: traduce 4xx en `PagoRechazado` y fallas de red, 5xx o circuito abierto en `CarteraNoDisponible` |
| Eventos | `eventos` | Manejadores (`ManejadorEvento<T>`), validación de payloads y armado de eventos hacia el outbox |
| Job | `jobs` | Recuperación de compras `RESERVADA` |
| Datos | `models`, `repositories` | Entidades JPA (`ddl-auto=validate`), `Specification`, `upsert` nativos y `FOR UPDATE` |
| Documentos | `documentos` | `BoletosPdf` (OpenPDF) y `GeneradorQr` (ZXing): PDF de una compra con un QR por boleto |
| Errores | `exception` | Jerarquía `BoletosServiceException` y `RespuestaError` uniforme |

---

## 3. Saga de compra en línea y job de recuperación

Boletos orquesta; Cartera cobra de forma síncrona e idempotente con `Clave-Idempotencia = idCompra`. Ninguna
transacción de la base queda abierta mientras se espera a Cartera.

```mermaid
sequenceDiagram
    autonumber
    actor U as Cliente
    participant B as CompraEnLineaService
    participant G as GestorCompras
    participant DB as bd_boletos
    participant C as Cartera
    participant J as RecuperacionComprasJob

    U->>B: POST /boletos/compras { idCompra, idViaje, asientos, fechaPago }
    B->>DB: ¿Existe idCompra?
    alt Ya existe
        alt CONFIRMADA (mismos datos)
            B-->>U: 200 (la original, sin cobrar)
        else ANULADA
            B-->>U: 409 COMPRA_ANULADA
        else RESERVADA
            Note over B: continúa en el paso del cobro
        end
    end
    B->>B: Valida comprador (perfil con NIT, activo), viaje (PROGRAMADO, con bus, no salió) y asientos
    B->>G: Tx1 reservar
    G->>DB: INSERT compra RESERVADA (vence en 10 min) + boletos RESERVADO
    alt uq_asiento_activo violado
        G-->>U: 409 ASIENTO_NO_DISPONIBLE
    end
    B->>C: POST /cartera/pagos (fuera de transacción, 3 intentos ante red/5xx)
    alt 201/200 { idPago }
        B->>G: Tx2 confirmar (FOR UPDATE de la compra)
        alt La compra sigue RESERVADA
            G->>DB: CONFIRMADA + boletos CONFIRMADO + outbox VentaBoletosRegistrada
            B-->>U: 201 CONFIRMADA
        else El viaje se eliminó mientras tanto (ANULADA sin idPago)
            G->>DB: guarda idPago + outbox BoletosAnulados (con idPago → Cartera reembolsa)
            B-->>U: 409 VIAJE_NO_DISPONIBLE
        end
    else 4xx (SALDO_INSUFICIENTE, PERFIL_INCOMPLETO, CUENTA_INACTIVA…)
        B->>G: Tx2 liberar (ANULADA, boletos ANULADO, sin evento)
        B-->>U: mismo status y codigo de Cartera
    else Sin respuesta / 5xx / circuito abierto
        B-->>U: 202 RESERVADA + Location
    end

    loop Cada 60 s
        J->>DB: compras RESERVADA con 2 min o más
        J->>C: GET /cartera/pagos?tipoReferencia=BOLETOS&idReferencia (X-Clave-Interna)
        alt 200 { idPago }
            J->>G: confirmar (igual que Tx2)
        else 404 y la reserva venció
            J->>G: liberar (motivo RESERVA_VENCIDA)
        else 404 vigente, o Cartera no responde
            J->>J: se revisa en el siguiente ciclo
        end
    end
```

Puntos de diseño:

- **El índice manda.** La validación previa de asientos ocupados solo mejora el mensaje; con dos compras simultáneas
  ambas pasan la validación y el `INSERT` de la segunda viola `uq_asiento_activo`. Como esa falla aborta la
  transacción de PostgreSQL, el 409 de la carrera usa un mensaje genérico.
- **Cobro fuera de transacción.** Si el servicio cae entre el cobro y la confirmación, la compra queda `RESERVADA` y
  el job la confirma al encontrar el pago. Cartera nunca cobra dos veces la misma compra, porque la clave de
  idempotencia es `idCompra`.
- **Bloqueo de la compra.** `confirmar`, `liberar` y la anulación por viaje leen la compra con `FOR UPDATE`: el job,
  la saga y `ViajeEliminado` no pisan sus cambios.
- **Reintentos y circuito.** Retry por fuera del circuit breaker: con el circuito abierto no se reintenta y la compra
  queda `RESERVADA` de inmediato.
- **Hora.** La salida del viaje y el vencimiento de la reserva usan el `Clock` del contexto. El job selecciona por
  `reserva_expira_en` (no por `creado_en`, que pone Hibernate con la hora del sistema).

---

## 4. Eliminación de un viaje

```mermaid
sequenceDiagram
    autonumber
    participant MQ as RabbitMQ
    participant D as DespachadorEventos
    participant M as ManejadorViajeEliminado
    participant A as AnulacionViajeService
    participant G as GestorCompras
    participant DB as bd_boletos

    MQ->>D: ViajeEliminado (boletos.viajes.q)
    D->>DB: BEGIN · INSERT evento_procesado ON CONFLICT DO NOTHING
    alt idEvento ya procesado
        D->>DB: COMMIT (se ignora el duplicado)
    else Evento nuevo
        D->>M: manejar(sobre)
        M->>A: anularViaje(idViaje)
        A->>DB: UPDATE viaje_ref SET eliminado = true
        loop Cada compra RESERVADA o CONFIRMADA del viaje
            A->>G: anularPorViaje(idCompra)
            G->>DB: SELECT compra … FOR UPDATE
            G->>DB: compra ANULADA (VIAJE_ELIMINADO) + boletos ANULADO
            G->>DB: INSERT outbox_evento (BoletosAnulados)
        end
        D->>DB: COMMIT (viaje, compras, eventos y procesado, juntos)
    end
    Note over D,DB: Error transitorio → 3 reintentos (1, 2, 4 s) y luego DLQ.<br/>JSON inválido o idViaje nulo → DLQ inmediato.
    MQ-->>MQ: BoletosAnulados → Cartera (reembolsa si canal EN_LINEA e idPago ≠ null), Notificaciones, Reportes
```

- Las compras de mostrador también se anulan; Cartera ignora su BoletosAnulados porque no tienen pago de cartera.
- Si una compra en línea estaba `RESERVADA` sin `idPago`, su primer BoletosAnulados no genera reembolso. Si después
  llega el pago (saga o job), se publica un segundo BoletosAnulados con el `idPago`.
- Un viaje eliminado deja de listarse y su mapa de asientos responde 404.

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
        SV[ticket-service<br/>Java 21 · OpenPDF + ZXing · 1 vCPU · 2 GiB<br/>min 1 · max 1 instancia]
        CA[customers-service]
        VI[trip-service]
    end

    subgraph Datos["Servicios administrados"]
        SQL[(Cloud SQL PostgreSQL · us-east1<br/>transportesgtdb · bd_boletos)]
        MQ{{CloudAMQP · RabbitMQ<br/>amqps:// · exchange transporte.eventos}}
    end

    subgraph CI["CI/CD"]
        GH[GitHub<br/>luisbaquiax/TransportesGT · main]
        CB[Cloud Build<br/>activador ticket-service]
        AR[(Artifact Registry<br/>cloud-run-source-deploy/transportesgt/ticket-service)]
    end

    GW -->|/v1/tickets/**| SV
    SV -->|Cloud SQL socket factory| SQL
    SV <-->|eventos outbox / colas| MQ
    SV -->|POST/GET /cartera/pagos| CA
    VI -->|GET asiento-maximo| SV
    GH -->|push / merge a main| CB
    CB -->|docker build TicketService/| AR
    CB -->|gcloud run services update| SV
    AR -.->|imagen :COMMIT_SHA| SV
```

| Elemento | Detalle |
|---|---|
| Imagen | `Dockerfile` en dos etapas: `maven:3.9-eclipse-temurin-21` compila (`./mvnw clean package`) y `bellsoft/liberica-openjdk-alpine:21` ejecuta `app.jar` |
| CI/CD | Cada merge a `main` dispara el activador de Cloud Build: `docker build TicketService -f TicketService/Dockerfile`, push a Artifact Registry con la etiqueta del commit y `gcloud run services update ticket-service` |
| Base de datos | Cloud SQL `unimarketcunoc:us-east1:transportesgtdb`, base `bd_boletos`, por la *socket factory* (`jdbc:postgresql:///bd_boletos?cloudSqlInstance=…&socketFactory=…`) |
| Configuración | Variables de entorno: `SPRING_PROFILES_DEFAULT=prod`, `SPRING_DATASOURCE_*`, `RABBITMQ_URL`, `JWT_SECRET` (el mismo de todos los servicios), `CARTERA_URL`, `CLAVE_INTERNA`, `JWT_ISSUER`, `SERVER_PORT=8080`, `SWAGGER_HABILITADO`, `DB_POOL_MAX` |
| Esquema | Flyway aplica las migraciones al arrancar; Hibernate solo valida. En la nube no se modifican migraciones ya aplicadas: los cambios van en una versión nueva |
| Instancias | Exactamente 1 (`min` y `max`). Varias serían seguras: el outbox y `evento_procesado` hacen idempotente el consumo |
| CPU | Hoy con CPU solo durante peticiones (valor por defecto). Sus jobs (`@Scheduled`) y los consumidores de RabbitMQ necesitan CPU sin tráfico HTTP: conviene `--no-cpu-throttling` |
| Llamadas | Cobra en Cartera (`POST /cartera/pagos` con el JWT reenviado) y el job de recuperación consulta `GET /cartera/pagos` con `X-Clave-Interna`. Viajes le consulta `asiento-maximo` |
| PDF | OpenPDF arma el documento y ZXing el QR; no necesitan configuración ni fuentes del sistema |
| Acceso | Público (`ingress=all`, sin IAM de invocación); los datos los protege el JWT. Pendiente: dejar público solo el gateway |
| Cuenta de servicio | La de Compute por defecto (`…-compute@developer.gserviceaccount.com`), compartida con los demás servicios |
| Salud | `/v1/tickets/actuator/health` (`liveness` y `readiness`) |
