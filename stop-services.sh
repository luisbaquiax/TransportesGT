#!/usr/bin/env bash
# stop-services.sh — detiene los servicios de spring-boot:run

PATTERN="${1:-spring-boot:run}"

echo "Buscando procesos: $PATTERN"
PIDS=$(pgrep -f "$PATTERN" || true)

if [ -z "$PIDS" ]; then
    echo "No hay procesos corriendo con ese patrón."
    exit 0
fi

echo "Matando PIDs: $PIDS"
kill $PIDS 2>/dev/null

# Esperar un poco y forzar si quedan
sleep 3
REMAIN=$(pgrep -f "$PATTERN" || true)
if [ -n "$REMAIN" ]; then
    echo "Forzando cierre de: $REMAIN"
    kill -9 $REMAIN 2>/dev/null
fi

echo "Listo."
