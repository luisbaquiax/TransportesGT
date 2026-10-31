#!/usr/bin/env bash
set -euo pipefail

compose_file=${1:?Falta la ruta del archivo Compose}
service=${2:?Falta el servicio}
image=${3:?Falta la imagen}
health_url=${4:?Falta la URL de salud}
archive=${5:?Falta el archivo de imagen}

[[ "$compose_file" = /* && -f "$compose_file" ]]
[[ "$service" =~ ^[a-z][a-z0-9-]*$ ]]
[[ "$image" =~ ^transportesgt/[a-z0-9-]+:[a-f0-9]{40}$ ]]
[[ "$health_url" == http://* || "$health_url" == https://* ]]
command -v curl >/dev/null
docker compose version

deploy_dir="$(dirname "$compose_file")/.deploy"
mkdir -p "$deploy_dir"
# Serializa también ejecuciones externas al workflow para este servicio.
exec 9>"$deploy_dir/$service.lock"
flock -w 600 9
override="$deploy_dir/$service.yml"
compose=(docker compose -f "$compose_file")
container=$("${compose[@]}" ps -aq "$service")
previous_image=''
if [[ -n "$container" ]]; then
  previous_image=$(docker inspect --format '{{.Config.Image}}' "$container")
fi

write_override() {
  printf 'services:\n  %s:\n    image: "%s"\n' "$service" "$1" > "$override"
}

healthy() {
  local attempt
  for attempt in {1..30}; do
    if curl --fail --silent --show-error --connect-timeout 2 --max-time 5 \
      "$health_url" >/dev/null; then
      return 0
    fi
    sleep 4
  done
  return 1
}

docker load -i "$archive"
write_override "$image"
compose+=(-f "$override")
if "${compose[@]}" up -d --no-deps --no-build --pull never "$service" && healthy; then
  printf 'Despliegue correcto: %s\n' "$image"
else
  printf 'Falló el despliegue de %s.\n' "$service" >&2
  if [[ -n "$previous_image" ]]; then
    write_override "$previous_image"
    "${compose[@]}" up -d --no-deps --no-build --pull never "$service"
    healthy || printf 'La versión anterior tampoco responde al health check.\n' >&2
  fi
  exit 1
fi
