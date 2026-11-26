#!/usr/bin/env bash
set -uo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
deploy="$(cd "$here/.." && pwd)"
root="$(cd "$deploy/.." && pwd)"
. "$here/lib.sh"

project="neocatecumenal-update-test"
smoke_port="${NEOCAT_SMOKE_PORT:-8088}"
export NEOCAT_SMOKE_PORT="$smoke_port"
base_url="http://localhost:$smoke_port"
revision="$(git -C "$root" rev-parse HEAD)"
probe_revision="abcdef1"
ping_token="ping-token-sentinel"
update="$deploy/bin/neocat-update"

backend_base="neocatecumenal-backend:local"
web_base="neocatecumenal-web:local"
backend_probe="neocatecumenal-backend:probe"
backend_broken="neocatecumenal-backend:broken"
backend_unhealthy="neocatecumenal-backend:unhealthy"

work="$(mktemp -d)"
export NEOCAT_HOME="$work/home"
export NEOCAT_CONFIG_DIR="$work/config"
export NEOCAT_COMPOSE_FILE="$deploy/compose.yaml:$deploy/compose.smoke.yaml:$work/override.yaml"
export NEOCAT_COMPOSE_PROJECT="$project"
export NEOCAT_ENV_FILE="$deploy/env/smoke.env"
export NEOCAT_HEALTH_TIMEOUT=90
state="$NEOCAT_HOME/state"
output="$work/update.out"
pings="$work/pings.log"
database_password="$(openssl rand -hex 16)"

mkdir -p "$NEOCAT_HOME" "$NEOCAT_CONFIG_DIR"
create_database_secrets "$NEOCAT_HOME" "$database_password"
: >"$pings"
: >"$output"

cat >"$work/override.yaml" <<YAML
volumes:
  neocatecumenal-pgdata:
    name: $project-pgdata
YAML

compose() {
  docker compose -p "$project" -f "$deploy/compose.yaml" -f "$deploy/compose.smoke.yaml" -f "$work/override.yaml" \
    --env-file "$deploy/env/smoke.env" "$@"
}

remove_probe_images() {
  docker rmi -f "$registry_host/neocat-backend:production" "$registry_host/neocat-web:production" \
    "$backend_probe" "$backend_broken" "$backend_unhealthy" \
    neocatecumenal-backend:previous neocatecumenal-web:previous >/dev/null 2>&1
}

registry_name="neocat-update-registry-$$"
registry_image="registry:3"
registry_host="127.0.0.1:0"
stub_pid=""
cleanup() {
  [ -n "$stub_pid" ] && kill "$stub_pid" >/dev/null 2>&1
  docker rm -f "$registry_name" >/dev/null 2>&1
  compose --profile ops down -v --remove-orphans >/dev/null 2>&1
  remove_probe_images
  rm -rf "$work"
}
trap cleanup EXIT

if [ ! -x "$update" ]; then
  echo "FALLO: falta deploy/bin/neocat-update ejecutable"
  exit 1
fi

cat >"$work/ping-stub.py" <<'PY'
import http.server
import sys

port_file, log_file = sys.argv[1], sys.argv[2]


class Handler(http.server.BaseHTTPRequestHandler):
    def respond(self):
        length = int(self.headers.get("Content-Length") or 0)
        if length:
            self.rfile.read(length)
        with open(log_file, "a") as log:
            log.write(self.command + " " + self.path + "\n")
        self.send_response(200)
        self.send_header("Content-Length", "2")
        self.end_headers()
        self.wfile.write(b"OK")

    do_GET = respond
    do_POST = respond

    def log_message(self, *args):
        pass


server = http.server.HTTPServer(("127.0.0.1", 0), Handler)
with open(port_file, "w") as handle:
    handle.write(str(server.server_address[1]))
server.serve_forever()
PY

python3 -I "$work/ping-stub.py" "$work/port" "$pings" &
stub_pid=$!
for _ in $(seq 1 50); do
  [ -s "$work/port" ] && break
  sleep 0.1
done
printf 'NEOCAT_PING_DEPLOY=http://127.0.0.1:%s/%s\n' "$(cat "$work/port")" "$ping_token" >"$NEOCAT_CONFIG_DIR/deploy.env"
chmod 600 "$NEOCAT_CONFIG_DIR/deploy.env"

build_images() {
  docker build --build-context "api=$root/api" --build-arg "SERVICE_REVISION=$revision" \
    -t "$backend_base" "$root/backend" >/dev/null || return 1
  if ! docker image inspect "$web_base" >/dev/null 2>&1; then
    docker build -f "$deploy/edge/Dockerfile" -t "$web_base" "$root" >/dev/null || return 1
  fi
}

derive() {
  local tag="$1" instructions="$2" context
  context="$(mktemp -d "$work/derive.XXXXXX")"
  printf 'CREATE TABLE broken_probe(id int);\nSELECT FROM FROM;\n' >"$context/V9999__broken.sql"
  printf 'FROM %s\n%s\n' "$backend_base" "$instructions" >"$context/Dockerfile"
  docker build -t "$tag" "$context" >/dev/null
}

build_derived_images() {
  derive "$backend_probe" "ENV SERVICE_REVISION=$probe_revision" || return 1
  derive "$backend_broken" "COPY --chown=10001:10001 V9999__broken.sql /broken/V9999__broken.sql
ENV SPRING_FLYWAY_LOCATIONS=classpath:db/migration,filesystem:/broken" || return 1
  derive "$backend_unhealthy" "ENV SERVER_PORT=9999" || return 1
}

stack_is_up() {
  BACKEND_IMAGE="$backend_base" WEB_IMAGE="$web_base" compose up -d --wait --wait-timeout 240
}

run_update() {
  "$update" "$@" >>"$output" 2>&1
}

running_image() {
  docker inspect -f '{{.Image}}' "$(compose ps -q "$1")"
}

image_id() {
  docker image inspect -f '{{.Id}}' "$1"
}

reported_revision() {
  curl -fsS "$base_url/api/compatibility" | python3 -I -c 'import json, sys; print(json.load(sys.stdin)["serviceRevision"])'
}

state_value() {
  sed -n "s/^$2=//p" "$state/$1.env"
}

ping_count() {
  grep -c -x "$1" "$pings"
}

last_ping() {
  tail -n 1 "$pings"
}

wait_for_revision() {
  local expected="$1" attempt
  for attempt in $(seq 1 30); do
    [ "$(reported_revision 2>/dev/null)" = "$expected" ] && return 0
    sleep 1
  done
  return 1
}

apply_without_changes_exits_zero() {
  run_update apply "$backend_base" "$web_base" \
    && [ ! -e "$state/previous.env" ] \
    && [ "$(last_ping)" = "POST /$ping_token" ]
}

apply_without_changes_keeps_the_running_container() {
  local before="$1"
  [ "$(docker inspect -f '{{.Id}}' "$(compose ps -q backend)")" = "$before" ]
}

apply_new_image_switches_and_records_previous() {
  local base_id
  base_id="$(image_id "$backend_base")"
  run_update apply "$backend_probe" "$web_base" \
    && [ "$(state_value current BACKEND_IMAGE)" = "$backend_probe" ] \
    && [ "$(state_value previous BACKEND_IMAGE)" = "neocatecumenal-backend:previous" ] \
    && [ "$(state_value previous WEB_IMAGE)" = "neocatecumenal-web:previous" ] \
    && [ "$(image_id neocatecumenal-backend:previous)" = "$base_id" ] \
    && [ "$(running_image backend)" = "$(image_id "$backend_probe")" ]
}

compatibility_shows_the_deployed_commit() {
  wait_for_revision "$probe_revision"
}

broken_migration_keeps_the_running_version() {
  local before_pings
  before_pings="$(ping_count "POST /$ping_token")"
  ! run_update apply "$backend_broken" "$web_base" \
    && [ "$(running_image backend)" = "$(image_id "$backend_probe")" ] \
    && [ "$(reported_revision)" = "$probe_revision" ] \
    && [ "$(state_value current BACKEND_IMAGE)" = "$backend_probe" ] \
    && grep -q "la migración falló" "$output" \
    && [ "$(last_ping)" = "POST /$ping_token/fail" ] \
    && [ "$(ping_count "POST /$ping_token")" = "$before_pings" ]
}

later_correct_run_is_green_again() {
  run_update apply "$backend_base" "$web_base" \
    && [ "$(last_ping)" = "POST /$ping_token" ] \
    && wait_for_revision "$revision"
}

unhealthy_image_rolls_back_by_itself() {
  local good_id
  good_id="$(image_id "$backend_base")"
  ! run_update apply "$backend_unhealthy" "$web_base" \
    && grep -q "se ha vuelto a la anterior" "$output" \
    && [ "$(last_ping)" = "POST /$ping_token/fail" ] \
    && [ "$(running_image backend)" = "$good_id" ] \
    && wait_for_revision "$revision"
}

web_stays_up_after_rollback() {
  [ "$(curl -s -o /dev/null -w '%{http_code}' "$base_url/")" = "200" ]
}

failure_ping_text_is_fixed() {
  [ "$(grep -c "$ping_token" "$pings")" -gt 0 ]
}

output_never_shows_secrets() {
  ! grep -q "$database_password" "$output" \
    && ! grep -q "$ping_token" "$output" \
    && ! grep -qi "authkey" "$output"
}

update_brings_up_tailscale_when_the_project_defines_it() {
  grep -q 'config --services' "$update" && grep -q 'up -d.*tailscale' "$update"
}

units_are_scheduled_every_two_minutes() {
  grep -q '^OnUnitActiveSec=2min$' "$deploy/systemd/neocat-update.timer" \
    && grep -q '^Persistent=true$' "$deploy/systemd/neocat-update.timer" \
    && grep -q '^Type=oneshot$' "$deploy/systemd/neocat-update.service" \
    && grep -q '^ExecStart=.*neocat-update$' "$deploy/systemd/neocat-update.service"
}

units_and_env_example_have_no_comments() {
  ! grep -nE '^\s*#[^!]' "$deploy/systemd/neocat-update.service" "$deploy/systemd/neocat-update.timer" \
      "$deploy/env/deploy.env.example" "$update"
}

deploy_env_example_has_no_values() {
  grep -q '^NEOCAT_PING_DEPLOY=$' "$deploy/env/deploy.env.example"
}

start_registry() {
  docker run -d --name "$registry_name" -p 127.0.0.1::5000 "$registry_image" >/dev/null || return 1
  local port attempt
  port="$(docker port "$registry_name" 5000/tcp | head -n 1 | sed 's/.*://')"
  registry_host="127.0.0.1:$port"
  for attempt in $(seq 1 30); do
    curl -fs "http://$registry_host/v2/" >/dev/null 2>&1 && return 0
    sleep 1
  done
  return 1
}

publish_as_production() {
  local repository="$1" image="$2"
  docker tag "$image" "$registry_host/$repository:production" \
    && docker push -q "$registry_host/$repository:production" >/dev/null
}

registry_digest_of() {
  docker image inspect -f '{{range .RepoDigests}}{{println .}}{{end}}' "$registry_host/$1:production" \
    | grep "^$registry_host/$1@" | head -n 1
}

run_scheduled_update() {
  NEOCAT_BACKEND_REPOSITORY="$registry_host/neocat-backend" NEOCAT_WEB_REPOSITORY="$registry_host/neocat-web" \
    "$update" >>"$output" 2>&1
}

failed_state_names_the_broken_digest() {
  grep -q "^BACKEND_IMAGE=$(registry_digest_of neocat-backend)$" "$state/failed.env" \
    && grep -q "^WEB_IMAGE=$(registry_digest_of neocat-web)$" "$state/failed.env"
}

unhealthy_production_rolls_back_and_pings_fail_once() {
  local good_id before_fails
  good_id="$(image_id "$backend_base")"
  publish_as_production neocat-backend "$backend_unhealthy" \
    && publish_as_production neocat-web "$web_base" \
    && : >"$pings" \
    && ! run_scheduled_update \
    && [ "$(ping_count "POST /$ping_token/fail")" = "1" ] \
    && [ "$(running_image backend)" = "$good_id" ] \
    && failed_state_names_the_broken_digest
}

unchanged_broken_production_is_skipped_without_touching_the_service() {
  local container_before fails_before
  container_before="$(docker inspect -f '{{.Id}}' "$(compose ps -q backend)")"
  fails_before="$(ping_count "POST /$ping_token/fail")"
  : >"$output"
  run_scheduled_update \
    && [ "$(ping_count "POST /$ping_token/fail")" = "$fails_before" ] \
    && [ "$(ping_count "POST /$ping_token")" = "0" ] \
    && [ "$(docker inspect -f '{{.Id}}' "$(compose ps -q backend)")" = "$container_before" ] \
    && grep -q "ya falló" "$output"
}

repeated_skips_never_repeat_the_failure_ping() {
  run_scheduled_update && run_scheduled_update \
    && [ "$(ping_count "POST /$ping_token/fail")" = "1" ]
}

broken_migration_in_production_is_also_skipped() {
  publish_as_production neocat-backend "$backend_broken" \
    && : >"$pings" \
    && ! run_scheduled_update \
    && [ "$(ping_count "POST /$ping_token/fail")" = "1" ] \
    && run_scheduled_update \
    && [ "$(ping_count "POST /$ping_token/fail")" = "1" ] \
    && failed_state_names_the_broken_digest
}

migration_output_tail_is_kept_in_state() {
  [ -s "$state/migrate.log" ] \
    && grep -q "V9999" "$state/migrate.log" \
    && ! grep -q "$database_password" "$state/migrate.log" \
    && grep -q "migrate.log" "$output" \
    && ! grep -q "V9999" "$pings"
}

new_production_digest_is_attempted_and_clears_the_failure() {
  publish_as_production neocat-backend "$backend_base" \
    && : >"$pings" \
    && run_scheduled_update \
    && [ ! -e "$state/failed.env" ] \
    && [ "$(last_ping)" = "POST /$ping_token" ] \
    && wait_for_revision "$revision"
}

manual_apply_ignores_the_failed_record() {
  printf 'BACKEND_IMAGE=%s\nWEB_IMAGE=%s\n' "$backend_base" "$web_base" >"$state/failed.env"
  run_update apply "$backend_base" "$web_base"
  local status=$?
  rm -f "$state/failed.env"
  [ "$status" -eq 0 ] && [ "$(last_ping)" = "POST /$ping_token" ]
}

check "las imágenes de backend y web existen" build_images
check "las imágenes derivadas de prueba se construyen" build_derived_images
check "el stack arranca sano con las imágenes locales" stack_is_up

container_before="$(docker inspect -f '{{.Id}}' "$(compose ps -q backend)" 2>/dev/null)"
check "apply sin cambios sale con 0, no guarda previous y hace ping de éxito" apply_without_changes_exits_zero
check "apply sin cambios no recrea el contenedor" apply_without_changes_keeps_the_running_container "$container_before"
check "una imagen nueva queda en current.env y la anterior en previous.env como :previous" apply_new_image_switches_and_records_previous
check "/api/compatibility muestra el commit desplegado" compatibility_shows_the_deployed_commit
check "una migración rota deja la versión anterior atendiendo, sale con ≠ 0 y hace ping /fail" broken_migration_keeps_the_running_version
check "una ejecución correcta posterior vuelve a verde" later_correct_run_is_green_again
check "una imagen que migra pero no llega a healthy vuelve sola a previous.env" unhealthy_image_rolls_back_by_itself
check "la web sigue sirviéndose tras la vuelta atrás" web_stays_up_after_rollback
check "un registro local sirve las etiquetas production" start_registry
check "un production roto vuelve atrás, avisa /fail una vez y guarda el digest fallido en failed.env" unhealthy_production_rolls_back_and_pings_fail_once
check "con el mismo digest fallido no se toca el servicio ni se repite el aviso" unchanged_broken_production_is_skipped_without_touching_the_service
check "varias ejecuciones seguidas no repiten /fail" repeated_skips_never_repeat_the_failure_ping
check "una migración rota en production también se salta después de un único /fail" broken_migration_in_production_is_also_skipped
check "las últimas líneas de la migración fallida quedan en state/migrate.log, sin secretos y sin ir al ping" migration_output_tail_is_kept_in_state
check "un digest nuevo en production se intenta y borra failed.env" new_production_digest_is_attempted_and_clears_the_failure
check "apply a mano ignora failed.env" manual_apply_ignores_the_failed_record
check "los pings llegan al servidor simulado" failure_ping_text_is_fixed
check "la salida nunca muestra secretos" output_never_shows_secrets
check "neocat-update levanta tailscale cuando el proyecto lo define" update_brings_up_tailscale_when_the_project_defines_it
check "neocat-update.service corre aislado con ProtectSystem=strict, ProtectHome, PrivateTmp, NoNewPrivileges y escritura mínima" unit_is_sandboxed "$deploy/systemd/neocat-update.service" "/srv/neocatecumenal/state"
check "systemd planifica neocat-update cada 2 minutos" units_are_scheduled_every_two_minutes
check "los ficheros de systemd, el env de ejemplo y la orden no llevan comentarios" units_and_env_example_have_no_comments
check "deploy.env.example no lleva valores" deploy_env_example_has_no_values

finish
