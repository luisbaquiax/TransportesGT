# Manual técnico — servicio-reportes (Reportes)

Microservicio `ReportingService` de TransportesGT. Mantiene proyecciones de lectura alimentadas solo por eventos y
genera los 12 reportes del enunciado (administrador del sistema y administrador de sucursal). No publica eventos ni
llama a otros servicios. Fuentes de verdad: `modelo-datos-postgresql-v4.md` §9, `catalogo-eventos-es-v4.md` §1–§3
y `plan-de-trabajo.md` §3.9.

| | |
|---|---|
| Stack | Java 21 · Spring Boot 4.1.1 · Spring Data JPA (Hibernate 7, SQL nativo para los reportes) · Flyway · Spring AMQP · Spring Security (resource server JWT) · springdoc |
| Librería de eventos | `com.github.luisbaquiax:EventosComunesTrasnportesGt:v2.1.1` (sobre, payloads, consumidor idempotente, topología) |
| Puerto / ruta base | `8089` / `/v1/reports` |
| Base de datos | PostgreSQL `bd_reportes` |
| Broker | RabbitMQ, exchange topic `transporte.eventos`, DLX `transporte.eventos.dlx` |
| PDF | JasperReports 7: `formato=pdf` genera el reporte con su plantilla `.jrxml` (`GeneradorReportesJasper`) |

---

## 1. Diagrama de tablas

Sin `FOREIGN KEY`: los eventos llegan desordenados y las uniones se hacen por `id` al consultar. Las líneas
punteadas son relaciones lógicas.

```mermaid
erDiagram
    rep_sucursal ||..o{ rep_bus : "id_sucursal"
    rep_sucursal ||..o{ rep_chofer : "id_sucursal"
    rep_sucursal ||..o{ rep_ruta : "origen / destino"
    rep_ruta ||..o{ rep_viaje : "id_ruta"
    rep_bus ||..o{ rep_viaje : "id_bus"
    rep_chofer ||..o{ rep_viaje : "id_chofer"
    rep_sucursal ||..o{ rep_ingreso : "id_sucursal"
    rep_viaje ||..o{ rep_ingreso : "id_viaje / id_alquiler"
    rep_sucursal ||..o{ rep_gasto : "id_sucursal"
    rep_bus ||..o{ rep_gasto : "id_bus"
    rep_viaje ||..o{ rep_gasto : "id_viaje"

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
    rep_sucursal {
        uuid id PK
        varchar nombre
        varchar departamento
        numeric latitud
        numeric longitud
        boolean activa
        bigint version
    }
    rep_bus {
        uuid id PK
        uuid id_sucursal
        varchar placa
        varchar marca
        varchar modelo
        smallint capacidad_pasajeros
        varchar estado_operativo
        boolean activo
        numeric kilometraje_actual "se actualiza con LlegadaRegistrada"
        bigint version
    }
    rep_chofer {
        uuid id PK
        uuid id_sucursal
        varchar numero_licencia
        varchar nombre_completo
        varchar tipo_licencia
        date fecha_vencimiento_licencia
        boolean activo
        bigint version
    }
    rep_ruta {
        uuid id PK
        uuid id_sucursal_origen
        uuid id_sucursal_destino
        numeric distancia_km
        numeric precio_boleto
        boolean eliminada
        bigint version
    }
    rep_viaje {
        uuid id PK
        varchar tipo_viaje
        varchar estado
        uuid id_ruta
        uuid id_alquiler
        uuid id_sucursal_origen
        varchar nombre_origen
        varchar nombre_destino
        uuid id_bus
        uuid id_chofer
        timestamptz fecha_hora_salida
        timestamptz fecha_hora_llegada_estimada
        timestamptz fecha_hora_salida_real
        timestamptz fecha_hora_llegada_real
        numeric kilometraje_inicial
        numeric kilometraje_final
        numeric distancia_recorrida_km
        boolean eliminado
        bigint version
    }
    rep_ingreso {
        uuid id PK
        varchar tipo_ingreso "VENTA_BOLETOS | ALQUILER"
        uuid id_referencia "idCompra / idAlquiler"
        uuid id_sucursal
        date fecha_ingreso "fecha de pago elegida por el usuario"
        numeric monto
        uuid id_viaje
        uuid id_ruta
        smallint cantidad_boletos
        varchar canal
        varchar nit_cliente
        varchar nombre_cliente
        varchar origen
        varchar destino
        timestamptz fecha_salida
        timestamptz fecha_retorno
        boolean anulado
        timestamptz anulado_en
        timestamptz creado_en
    }
    rep_gasto {
        uuid id PK
        varchar tipo_gasto "COMBUSTIBLE | TALLER | DEPRECIACION | SALARIO_CHOFER"
        uuid id_referencia "idGasto / idLinea / idDepreciacion / idPago"
        uuid id_sucursal
        uuid id_bus
        uuid id_viaje
        date fecha_gasto
        numeric monto
        uuid id_categoria
        varchar nombre_categoria
        varchar tipo_linea "MANO_DE_OBRA | REPUESTO"
        varchar descripcion
        numeric distancia_km
        numeric monto_por_km
        timestamptz creado_en
    }
    rep_tarifa_depreciacion {
        date vigente_desde PK
        numeric monto_por_km
    }
```

La vista `v_resumen_diario_sucursal` suma ingresos no anulados y gastos por sucursal y día (base del reporte de
ganancias). `outbox_evento` existe solo porque el publicador de la librería arranca siempre.

## 2. Diagrama de componentes

```mermaid
flowchart LR
    subgraph HTTP["controllers"]
        CA[ReportesAdminController<br/>reportes 1-6]
        CS[ReportesSucursalController<br/>reportes 7-12]
        CC[CatalogosController]
    end
    subgraph Comun["services/comun"]
        VF[ValidadorFiltros]
        AS[AlcanceSucursal<br/>regla del token]
        AR[ArmadorReporteTabla<br/>grupos · subtotales · totales]
        RR[RespuestaReporte<br/>JSON o PDF]
    end
    subgraph Reportes["services/reportsadmin · reportssucursal"]
        R1[6 servicios de sistema]
        R2[6 servicios de sucursal]
        CAT[catalogos<br/>CatalogoService]
    end
    subgraph Exportacion["services/exportacion"]
        GP[[GeneradorPdf]]
        GJ[GeneradorReportesJasper<br/>JasperReports 7]
        AD[12 ReporteJasper<br/>adaptadores por reporte]
        PL[CatalogoPlantillasJasper<br/>plantillas .jrxml]
    end
    subgraph Repos["repositories (SQL nativo, proyecciones)"]
        Q1[reportsadmin/*]
        Q2[reportssucursal/*]
        Q3[catalogos/*]
        UP[Rep*Repository<br/>upserts e inserts]
    end
    subgraph Eventos["eventos"]
        DE[[DespachadorEventos<br/>librería: idempotencia, DLQ]]
        MH[23 Manejador*<br/>solo delegan]
        PS[services/proyecciones<br/>Sucursal · Flota · Viajes · Ingresos · Gastos]
    end
    CA & CS --> VF & RR
    CA --> R1
    CS --> R2
    CC --> CAT --> Q3
    R1 & R2 & CAT --> AS
    R1 & R2 --> AR
    R1 --> Q1
    R2 --> Q2
    RR --> GP
    GP -.-> GJ --> AD & PL
    DE --> MH --> PS --> UP
    UP & Q1 & Q2 & Q3 --> DB[(bd_reportes)]
```

- Una interfaz por caso de uso y su implementación en `impl/`; los controladores dependen solo de interfaces.
- Sin métodos estáticos propios: los helpers (`AlcanceSucursal`, `ArmadorReporteTabla`, `TextosReporte`,
  `FilasTaller`, `Dinero`, `Fechas`) son `@Component`.
- Cada reporte arma un `ReporteTabla` (título, filtros, `generadoEn`, grupos con subtotales y totales) que sirve
  tanto para JSON como para el futuro PDF.

## 3. Cómo cada evento alimenta cada tabla

```mermaid
flowchart LR
    ID[[Identidad]] -->|SucursalCreada/Actualizada| S[(rep_sucursal)]
    FL[[Flota]] -->|BusRegistrado/Actualizado| B[(rep_bus)]
    FL -->|ChoferRegistrado/Actualizado| C[(rep_chofer)]
    VI[[Viajes]] -->|RutaCreada/Actualizada/Eliminada| R[(rep_ruta)]
    VI -->|ViajeProgramado/Actualizado/Eliminado| V[(rep_viaje)]
    VI -->|SalidaRegistrada| V
    VI -->|"SalidaRegistrada: EN_TRANSITO"| B
    VI -->|LlegadaRegistrada: FINALIZADO| V
    VI -->|"LlegadaRegistrada: GREATEST(km), DISPONIBLE"| B
    BO[[Boletos]] -->|VentaBoletosRegistrada / BoletosAnulados| I[(rep_ingreso)]
    AL[[Alquileres]] -->|AlquilerConfirmado / AlquilerCancelado| I
    CO[[Costos]] -->|Combustible · Depreciación · Taller por línea · PagoChofer| G[(rep_gasto)]
    CO -->|TarifaDepreciacionCambiada| T[(rep_tarifa_depreciacion)]
```

| Evento | Tipo | Efecto | Si falta lo que necesita |
|---|---|---|---|
| Sucursal / Bus / Chofer / Ruta / Viaje (Creado, Actualizado) | Instantánea | Upsert si `versionAgregado` es mayor. `BusActualizado` no toca el kilometraje. `rep_viaje.estado` nunca baja de `FINALIZADO` | — (crea la fila) |
| `RutaEliminada`, `ViajeEliminado` | Hecho | `eliminada` / `eliminado = true` | Transitorio (reintento) |
| `SalidaRegistrada` | Hecho | Salida real e inicial; bus `EN_TRANSITO` (no si la llegada ya se aplicó) | Transitorio |
| `LlegadaRegistrada` | Hecho | Llegada, km final, distancia, `FINALIZADO`; bus `GREATEST(km)` y `DISPONIBLE` | Transitorio (sin bus proyectado solo avisa) |
| `VentaBoletosRegistrada` | Hecho | Ingreso `VENTA_BOLETOS` (`cantidad_boletos = asientos.size()`) | — |
| `BoletosAnulados`, `AlquilerCancelado` | Hecho | `anulado = true` sobre la fecha de pago original | Transitorio: no crea filas incompletas |
| `AlquilerConfirmado` | Hecho | Ingreso `ALQUILER` (canal `MOSTRADOR` si no hay `idPago`) | — |
| Gastos de Costos | Hecho | Una fila por gasto; taller una por línea; el salario toma el bus del viaje | — |
| `TarifaDepreciacionCambiada` | Hecho | Upsert por `vigente_desde` | — |

Un evento sin su id o sin datos es **no recuperable** (DLQ inmediata). Un JSON inválido lo manda a la DLQ la
librería.

### Consumo y reintentos

```mermaid
sequenceDiagram
    participant MQ as RabbitMQ (reportes.boletos.q)
    participant C1 as Consumidor 1
    participant C2 as Consumidor 2
    participant DB as bd_reportes
    MQ->>C1: BoletosAnulados(idCompra)
    C1->>DB: BEGIN · evento_procesado · ¿existe la venta? no
    C1-->>DB: ROLLBACK (ProyeccionNoDisponibleException)
    Note over C1: reintento en 1 s, 2 s, 4 s, 8 s
    MQ->>C2: VentaBoletosRegistrada(idCompra)
    C2->>DB: BEGIN · evento_procesado · INSERT rep_ingreso · COMMIT
    C1->>DB: reintento: BEGIN · UPDATE anulado = true · COMMIT
    Note over C1,MQ: tras 5 intentos fallidos el mensaje va a reportes.boletos.dlq
```

## 4. Diagrama de despliegue

Producción en Google Cloud (proyecto `unimarketcunoc`). En local, `infra/docker-compose.yml` levanta lo mismo con
PostgreSQL y RabbitMQ en contenedores y el servicio en el puerto 8089.

```mermaid
flowchart TB
    U([Navegador]) -->|HTTPS| FE[Frontend React<br/>Vercel · transporte-gt-01.vercel.app]
    FE -->|HTTPS + JWT| GW

    subgraph CR["Cloud Run · northamerica-south1"]
        GW[gateway-service<br/>Spring Cloud Gateway · público]
        RE[reporting-service<br/>Java 21 · JasperReports · 1 vCPU · 2 GiB<br/>min 1 · max 1 instancia]
        PROD[identity · fleet · trip · ticket<br/>operation · customers]
    end
    AL[private-rental-service<br/>Cloud Run · europe-west1]

    subgraph Datos["Servicios administrados"]
        SQL[(Cloud SQL PostgreSQL · us-east1<br/>transportesgtdb · bd_reportes)]
        MQ{{CloudAMQP · RabbitMQ<br/>amqps:// · exchange transporte.eventos}}
    end

    subgraph CI["CI/CD"]
        GH[GitHub<br/>luisbaquiax/TransportesGT · main]
        CB[Cloud Build<br/>activador reporting-service]
        AR[(Artifact Registry<br/>cloud-run-source-deploy/transportesgt/reporting-service)]
    end

    GW -->|/v1/reports/**| RE
    RE -->|Cloud SQL socket factory| SQL
    PROD & AL -->|eventos outbox| MQ
    MQ -->|reportes.*.q| RE
    GH -->|push / merge a main| CB
    CB -->|docker build ReportingService/| AR
    CB -->|gcloud run services update| RE
    AR -.->|imagen :COMMIT_SHA| RE
```

| Elemento | Detalle |
|---|---|
| Imagen | `Dockerfile` en dos etapas: `maven:3.9-eclipse-temurin-21` compila (`./mvnw clean package`) y `eclipse-temurin:21-jre` ejecuta `app.jar` con `-Djava.awt.headless=true` (JasperReports genera los PDF sin pantalla) |
| CI/CD | Cada merge a `main` dispara el activador de Cloud Build: `docker build ReportingService -f ReportingService/Dockerfile`, push a Artifact Registry con la etiqueta del commit y `gcloud run services update reporting-service` |
| Base de datos | Cloud SQL `unimarketcunoc:us-east1:transportesgtdb`, base `bd_reportes`, por la *socket factory* (`jdbc:postgresql:///bd_reportes?cloudSqlInstance=…&socketFactory=…`). Pool de 5 conexiones (`DB_POOL_MAX`) |
| Configuración | Variables de entorno: `SPRING_PROFILES_DEFAULT=prod`, `SPRING_DATASOURCE_*`, `RABBITMQ_URL`, `JWT_SECRET` (el mismo de todos los servicios), `JWT_ISSUER`, `SERVER_PORT=8080`, `SWAGGER_HABILITADO`, `DB_POOL_MAX` |
| Esquema | Flyway aplica `V1` y `V2` al arrancar; Hibernate solo valida. En la nube no se modifican migraciones ya aplicadas: los cambios van en una `V3` nueva |
| Instancias | Exactamente 1 (`min` y `max`) porque consume colas. Varias serían seguras por la idempotencia de `evento_procesado` |
| CPU | Hoy con CPU solo durante peticiones (valor por defecto). Para que los consumidores procesen eventos sin tráfico HTTP conviene `--no-cpu-throttling` |
| Acceso | Público (`ingress=all`, sin IAM de invocación); los datos los protege el JWT. Pendiente: dejar público solo el gateway |
| Cuenta de servicio | La de Compute por defecto (`…-compute@developer.gserviceaccount.com`), compartida con los demás servicios |
| Orden de arranque | Reportes antes que los productores, para que sus colas existan (ver README, "Reponer eventos"). Si se pierden eventos, la página **Sincronización** los republica |
| Salud | `/v1/reports/actuator/health` (`liveness` y `readiness`); el arranque tarda cerca de 90 s |

## 5. Supuestos y decisiones

- **Filtros por fecha de negocio:** ingresos por `fecha_ingreso` (fecha de pago elegida por el usuario); gastos por
  `fecha_gasto` (fecha de servicio de taller, de cálculo de depreciación, del gasto de combustible o del pago al
  chofer). Nunca por `ocurridoEn` (decisión #9 del catálogo). En buses y choferes, el intervalo limita los viajes
  realizados por fecha de llegada real (hora de Guatemala).
- **Salario del chofer como gasto** (`SALARIO_CHOFER`), incluido en gastos y ganancias (plan §3.9).
- **Anulación retroactiva:** anular una venta o un alquiler marca el ingreso sobre su fecha de pago original, así
  que el reporte de ese periodo cambia.
- **Taller:** el reporte de gastos separa mano de obra (`TALLER`) y repuesto (`REPUESTO`) según `tipo_linea`.
- **Chofer actual de un bus:** el del viaje `EN_TRANSITO`; si no, el del próximo `PROGRAMADO` (desde ahora); si no,
  vacío.
- **Tarifa de depreciación del reporte 11:** la vigente hoy (`vigente_desde <= hoy`, la más reciente); el promedio
  aplicado sale de lo realmente cobrado en cada viaje.
- **"Hoy"** (tarifa vigente, licencias por vencer, próximo viaje) sale del `Clock` en America/Guatemala.
- **Sucursales** en orden alfabético con reglas del español (`Collator`), igual en todos los reportes.
- **Formato de error y correlación:** se mantiene `RespuestaError` y `X-Id-Correlacion` del código existente (el
  plan §2.3 proponía `ProblemDetail`).
