#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
log_dir="${LOCAL_RUN_LOG_DIR:-$root/.local-run}"
mkdir -p "$log_dir"

sdk_java="$(sed -n 's/^java=//p' "$root/.sdkmanrc")"
if [ -n "$sdk_java" ] && [ -x "$HOME/.sdkman/candidates/java/$sdk_java/bin/java" ]; then
  export JAVA_HOME="$HOME/.sdkman/candidates/java/$sdk_java"
else
  export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
fi

mode="web"
case "${1:-}" in
  "") ;;
  --device) mode="device" ;;
  *) echo "usage: $0 [--device]" >&2; exit 2 ;;
esac

lan_ip() {
  local ip
  for iface in en0 en1; do
    ip="$(ipconfig getifaddr "$iface" 2>/dev/null || true)"
    if [ -n "$ip" ]; then
      echo "$ip"
      return 0
    fi
  done
  return 1
}

if [ "$mode" = "device" ]; then
  if ! host_ip="$(lan_ip)"; then
    echo "no LAN IP found on en0 or en1; connect to Wi-Fi or Ethernet" >&2
    exit 1
  fi
  export EXPO_PUBLIC_API_URL="${EXPO_PUBLIC_API_URL:-http://$host_ip:8080}"
else
  export EXPO_PUBLIC_API_URL="${EXPO_PUBLIC_API_URL:-http://localhost:8080}"
fi

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
if [ "$mode" = "device" ]; then
  npx expo start --lan
else
  npm run web
fi
