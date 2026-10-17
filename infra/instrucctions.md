docker compose --env-file .env up -d --build

docker compose up -d

### Cambio en código Java: debes reconstruir la imagen:
```bash
docker compose up -d --build identity-service
```
### Para reconstruir y actualizar todos:

```bash
docker compose up -d --build
```

### Cambio en docker-compose.yml o .env:
```bash
docker compose up -d
```

### Solo reiniciar un servicio sin reconstruir:
```bash
docker compose restart identity-service
```

#### Ejemplos de nombres disponibles: fleet-service, trip-service, ticket-service, api-gateway, etc.

#### Los datos de PostgreSQL y RabbitMQ se conservan porque están en volúmenes.  

### Eliminar datos en base de datos y RabbitMQ
```bash
docker compose down -v
```