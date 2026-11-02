# Diagrama de arquitectura — TransportesGT

TransportesGT es una plataforma de **microservicios** para una empresa de transporte extraurbano. El frontend web
habla solo con el **API Gateway**, que valida el JWT y enruta cada prefijo `/v1/<servicio>/**` al microservicio que
corresponde. Cada microservicio es dueño de sus datos (**una base PostgreSQL por servicio**) y se comunica con los
demás de dos formas:

- **Eventos (asíncrono):** la vía principal. Cada servicio publica sus cambios en el broker con un *outbox*
  transaccional, y los demás mantienen sus propias proyecciones consumiéndolos de forma idempotente.
- **REST (síncrono):** solo donde se necesita una respuesta inmediata: cobrar en la cartera y dos validaciones
  entre servicios.

```mermaid
flowchart TB
    UI[Frontend web<br/>React · Vercel]

    GW[API Gateway<br/>valida JWT · CORS · enruta /v1/*]

    subgraph SERVICES["Microservicios de negocio"]
        IAM[Identity & Administration]
        FLEET[Fleet Service]
        TRIPS[Routes & Trips Service]
        TICKETS[Ticketing Service]
        CUSTOMERS[Customer & Wallet Service]
        RENTALS[Private Rentals Service]
        COSTS[Operational Costs Service]
        NOTIFY[Notification Service]
        REPORTS[Reporting Service]
    end

    subgraph DATA["Bases de datos (PostgreSQL · una por servicio)"]
        DB_IAM[(bd_identidad)]
        DB_FLEET[(bd_flota)]
        DB_TRIPS[(bd_viajes)]
        DB_TICKETS[(bd_boletos)]
        DB_CUSTOMERS[(bd_clientes)]
        DB_RENTALS[(bd_alquileres)]
        DB_COSTS[(bd_costos)]
        DB_NOTIFY[(bd_notificaciones)]
        DB_REPORTS[(bd_reportes)]
    end

    BROKER{{"Broker de eventos<br/>RabbitMQ (CloudAMQP)"}}

    UI --> GW
    GW --> IAM & FLEET & TRIPS & TICKETS & CUSTOMERS
    GW --> RENTALS & COSTS & NOTIFY & REPORTS

    TICKETS -->|REST: pago| CUSTOMERS
    RENTALS -->|REST: pago| CUSTOMERS
    FLEET -->|REST: viajes activos| TRIPS
    TRIPS -->|REST: asiento máximo| TICKETS

    IAM --- DB_IAM
    FLEET --- DB_FLEET
    TRIPS --- DB_TRIPS
    TICKETS --- DB_TICKETS
    CUSTOMERS --- DB_CUSTOMERS
    RENTALS --- DB_RENTALS
    COSTS --- DB_COSTS
    NOTIFY --- DB_NOTIFY
    REPORTS --- DB_REPORTS

    IAM & FLEET & TRIPS & TICKETS -.->|publican| BROKER
    CUSTOMERS & RENTALS & COSTS -.->|publican| BROKER

    BROKER -.->|consumen| FLEET & TRIPS & TICKETS & CUSTOMERS
    BROKER -.->|consumen| RENTALS & COSTS & NOTIFY & REPORTS

    classDef infra fill:#eeeeee,stroke:#555555,color:#111111
    classDef db fill:#e8f3f7,stroke:#176b87,color:#111111
    class GW,BROKER infra
    class DB_IAM,DB_FLEET,DB_TRIPS,DB_TICKETS,DB_CUSTOMERS,DB_RENTALS,DB_COSTS,DB_NOTIFY,DB_REPORTS db
```

## Responsabilidades

| Servicio | Responsabilidad |
|---|---|
| Identity & Administration | Usuarios, roles, sucursales, inicio de sesión (emite el JWT) y enlaces de contraseña |
| Fleet Service | Buses y choferes: estado operativo, licencias, kilometraje y fotos |
| Routes & Trips Service | Rutas, programación de viajes y registros inmutables de salida y llegada |
| Ticketing Service | Venta de boletos en línea y en mostrador, asientos, PDF con QR y abordaje |
| Customer & Wallet Service | Perfil comercial del cliente (NIT) y cartera digital: recargas, pagos y reembolsos |
| Private Rentals Service | Solicitud, cotización, pago y seguimiento de alquileres de buses |
| Operational Costs Service | Depreciación, gastos de taller y costos de cada viaje (combustible, depreciación y salario) |
| Notification Service | Correos de confirmación, enlaces de contraseña y avisos de vencimiento de licencia |
| Reporting Service | Proyecciones de lectura y los 12 reportes (JSON y PDF con JasperReports) |

## Comunicación síncrona (REST)

| Desde | Hacia | Para qué |
|---|---|---|
| Ticketing | Customer & Wallet | Cobrar la compra en línea (`POST/GET /cartera/pagos`) |
| Private Rentals | Customer & Wallet | Cobrar el alquiler en línea (`POST/GET /cartera/pagos`) |
| Fleet | Routes & Trips | Revisar viajes activos antes de desactivar un bus o chofer (`GET /viajes/activos`) |
| Routes & Trips | Ticketing | Revisar el asiento máximo vendido antes de cambiar el bus (`asiento-maximo`) |

## Notas

- **Productores y consumidores:** Notification y Reporting solo consumen eventos. Identity solo publica.
- **Alquiler -> viaje:** Rentals y Trips se coordinan por eventos, no por REST. `AlquilerConfirmado` crea el viaje en
  Trips, y Trips responde con `ViajeProgramado/Actualizado/Eliminado` y `LlegadaRegistrada`.
- **Detalle de cada servicio:** el catálogo de eventos (`catalogo-eventos-es-v4.md`) y los manuales técnicos
  (`manual-tecnico-*.md`, con tablas, componentes y despliegue).
