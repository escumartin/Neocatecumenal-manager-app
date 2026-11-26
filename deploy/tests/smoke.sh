#!/usr/bin/env bash
set -uo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
deploy="$(cd "$here/.." && pwd)"
root="$(cd "$deploy/.." && pwd)"
. "$here/lib.sh"

project="neocatecumenal-smoke"
base_url="http://localhost:8088"
revision="$(git -C "$root" rev-parse HEAD)"

set -a
. "$deploy/env/smoke.env"
set +a

work="$(mktemp -d)"
export NEOCAT_HOME="$work/home"
mkdir -p "$NEOCAT_HOME/secrets"
chmod 700 "$NEOCAT_HOME" "$NEOCAT_HOME/secrets"
printf '%s' "neocatecumenal" >"$NEOCAT_HOME/secrets/spring.datasource.username"
printf '%s' "$(openssl rand -hex 16)" >"$NEOCAT_HOME/secrets/spring.datasource.password"
chmod 600 "$NEOCAT_HOME"/secrets/*

compose() {
  docker compose -p "$project" -f "$deploy/compose.yaml" -f "$deploy/compose.smoke.yaml" "$@"
}

cleanup() {
  compose --profile ops down -v --remove-orphans >/dev/null 2>&1
  rm -rf "$work"
}
trap cleanup EXIT

ensure_images() {
  if ! docker image inspect "$BACKEND_IMAGE" >/dev/null 2>&1; then
    echo "construyendo $BACKEND_IMAGE"
    docker build --build-context "api=$root/api" --build-arg "SERVICE_REVISION=$revision" \
      -t "$BACKEND_IMAGE" "$root/backend" >/dev/null || return 1
  fi
  if ! docker image inspect "$WEB_IMAGE" >/dev/null 2>&1; then
    echo "construyendo $WEB_IMAGE"
    docker build -f "$deploy/edge/Dockerfile" -t "$WEB_IMAGE" "$root" >/dev/null || return 1
  fi
}

stack_comes_up_healthy() {
  compose up -d --wait --wait-timeout 240
}

health_is_up() {
  [ "$(curl -fsS "$base_url/api/health")" = '{"status":"UP"}' ]
}

compatibility_reports_the_commit() {
  local body reported minimum
  body="$(curl -fsS "$base_url/api/compatibility")" || return 1
  reported="$(python3 -I -c 'import json, sys; print(json.load(sys.stdin)["serviceRevision"])' <<<"$body")" || return 1
  minimum="$(python3 -I -c 'import json, sys; print(json.load(sys.stdin)["minimumAppVersion"])' <<<"$body")" || return 1
  [ "$minimum" = "$MINIMUM_APP_VERSION" ] && [ -n "$reported" ] && [[ "$revision" == "$reported"* ]]
}

actuator_is_hidden() {
  [ "$(curl -s -o /dev/null -w '%{http_code}' "$base_url/actuator/health")" = "404" ]
}

web_is_served() {
  curl -fsS "$base_url/" | grep -qi "<html"
}

only_edge_publishes_ports() {
  compose ps --format json | python3 -I -c '
import json, sys

published = {}
for line in sys.stdin.read().splitlines():
    if not line.strip():
        continue
    entry = json.loads(line)
    ports = [p for p in entry.get("Publishers") or [] if p.get("PublishedPort")]
    if ports:
        published[entry["Service"]] = ports
if set(published) != {"edge"}:
    sys.exit(1)
ports = published["edge"]
ok = all(p.get("URL") == "127.0.0.1" and p.get("PublishedPort") == 8088 for p in ports)
sys.exit(0 if ok else 1)
'
}

postgres_publishes_nothing() {
  local published
  published="$(docker inspect -f '{{json .NetworkSettings.Ports}}' "$(compose ps -q postgres)")"
  ! grep -q "HostPort" <<<"$published"
}

check "las imágenes locales de backend y web existen" ensure_images
check "postgres, backend y edge arrancan sanos" stack_comes_up_healthy
check "/api/health responde UP" health_is_up
check "/api/compatibility muestra el commit y la versión mínima" compatibility_reports_the_commit
check "/actuator/health responde 404" actuator_is_hidden
check "la web se sirve por edge" web_is_served
check "solo edge publica un puerto, en 127.0.0.1:8088" only_edge_publishes_ports
check "postgres no publica puerto" postgres_publishes_nothing

finish
