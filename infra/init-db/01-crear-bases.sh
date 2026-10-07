#!/usr/bin/env bash
# Se ejecuta UNA sola vez, cuando el volumen de datos de PostgreSQL está vacío.
# Crea un usuario (svc_<servicio>) y una base de datos (bd_<servicio>) por microservicio.
#
set -euo pipefail

SERVICIOS=(identidad flota viajes boletos clientes alquileres costos notificaciones reportes)

psql_admin() {
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "${2:-postgres}" -c "$1"
}

for s in "${SERVICIOS[@]}"; do
  echo ">> Creando usuario svc_${s} y base bd_${s}"
  psql_admin "CREATE ROLE svc_${s} LOGIN PASSWORD '${SVC_DB_PASSWORD}'"
  psql_admin "CREATE DATABASE bd_${s} OWNER svc_${s}"
  psql_admin "REVOKE ALL ON DATABASE bd_${s} FROM PUBLIC"
done

# bd_viajes necesita btree_gist para la restricción EXCLUDE
# (evita que un bus o un chofer queden en dos viajes que se traslapan)
echo ">> Habilitando btree_gist en bd_viajes"
psql_admin "CREATE EXTENSION IF NOT EXISTS btree_gist" bd_viajes

echo ">> Listo: 9 bases creadas"