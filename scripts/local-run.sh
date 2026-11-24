#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
log_dir="${LOCAL_RUN_LOG_DIR:-$root/.local-run}"
mkdir -p "$log_dir"

export JAVA_HOME="${JAVA_HOME:-$HOME/.sdkman/candidates/java/21-tem}"
export EXPO_PUBLIC_API_URL="${EXPO_PUBLIC_API_URL:-http://localhost:8080}"

docker compose -f "$root/docker-compose.yml" up -d --wait postgres

(cd "$root/backend" && ./mvnw -B -q spring-boot:run -Dspring-boot.run.profiles=local) >"$log_dir/backend.log" 2>&1 &
backend_pid=$!
trap 'kill "$backend_pid" 2>/dev/null || true' EXIT INT TERM

for _ in $(seq 1 120); do
  if curl -fsS http://localhost:8080/api/health >/dev/null 2>&1; then
    break
  fi
  if ! kill -0 "$backend_pid" 2>/dev/null; then
    echo "backend exited, see $log_dir/backend.log" >&2
    exit 1
  fi
  sleep 2
done

echo "backend up on http://localhost:8080 (log: $log_dir/backend.log)"
cd "$root/app"
npm run web
