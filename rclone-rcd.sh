#!/usr/bin/env bash
#
# Sobe o rclone remote control daemon (rcd) localmente, no mesmo endereço que a
# app espera por padrão (quarkus.rest-client.rclone.url, localhost:5572).
#
# Substitui o container docker-compose do rclone: rodando local, o rcd enxerga
# o mesmo filesystem do host que a app (cache.dir deixa de precisar de volume
# compartilhado).

set -euo pipefail

LOG_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/data/rclone-logs"
LOG_FILE="$LOG_DIR/rclone.log"

mkdir -p "$LOG_DIR"

echo "rcd escutando em 127.0.0.1:5572, log em $LOG_FILE"

exec rclone rcd \
    --rc-no-auth \
    --rc-addr 127.0.0.1:5572 \
    --log-file "$LOG_FILE" \
    --log-level INFO
