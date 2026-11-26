#!/usr/bin/env bash

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
deploy="$(cd "$here/.." && pwd)"
root="$(cd "$deploy/.." && pwd)"
. "$here/lib.sh"

restic_image="restic/restic@sha256:93999df80d4ac6cb148e82d2ef1fe6a81996f17ff535e772723538211b31555a"
temporary_root="${NEOCAT_TEST_TMP:-$HOME/desarrollo/java/tmp/neocatecumenal/004}"
mkdir -p "$temporary_root"
work="$(mktemp -d "$temporary_root/$project.XXXXXX")"

export NEOCAT_HOME="$work/home"
export NEOCAT_CONFIG_DIR="$work/config"
export NEOCAT_COMPOSE_FILE="$deploy/compose.yaml:$work/override.yaml"
export NEOCAT_COMPOSE_PROJECT="$project"
export NEOCAT_ENV_FILE="$deploy/env/smoke.env"
export NEOCAT_BACKUP_REQUIRE_MOUNT=false
export NEOCAT_BACKUP_MAX_USE=99
export NEOCAT_RESTIC_IMAGE="$restic_image"

pings="$work/pings.log"
output="$work/command.out"
database_password="$(openssl rand -hex 16)"
stub_pid=""

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
  docker compose -p "$project" -f "$deploy/compose.yaml" -f "$work/override.yaml" \
    --env-file "$deploy/env/smoke.env" "$@"
}

cleanup() {
  [ -n "$stub_pid" ] && kill "$stub_pid" >/dev/null 2>&1
  compose down -v --remove-orphans >/dev/null 2>&1
  rm -rf "$work"
}
trap cleanup EXIT

start_ping_stub() {
  python3 -I "$here/ping-stub.py" "$work/port" "$pings" &
  stub_pid=$!
  for _ in $(seq 1 50); do
    [ -s "$work/port" ] && break
    sleep 0.1
  done
  ping_base="http://127.0.0.1:$(cat "$work/port")"
}

use_repository() {
  export NEOCAT_BACKUP_DIR="$work/backups-$1"
  export RESTIC_REPOSITORY="$NEOCAT_BACKUP_DIR/restic"
  export RESTIC_PASSWORD_FILE="$work/restic-$1.password"
  new_restic_password
  mkdir -p "$NEOCAT_BACKUP_DIR"
}

new_restic_password() {
  rm -f "$RESTIC_PASSWORD_FILE"
  restic_secret="$(openssl rand -hex 16)"
  (umask 277 && printf '%s' "$restic_secret" >"$RESTIC_PASSWORD_FILE")
}

write_backup_env() {
  {
    printf 'RESTIC_REPOSITORY=%s\n' "$RESTIC_REPOSITORY"
    printf 'RESTIC_PASSWORD_FILE=%s\n' "$RESTIC_PASSWORD_FILE"
    printf 'NEOCAT_PING_BACKUP=%s/backup-token\n' "$ping_base"
    printf 'NEOCAT_PING_ERASURE_SYNC=%s/erasure-token\n' "$ping_base"
  } >"$NEOCAT_CONFIG_DIR/backup.env"
  chmod 600 "$NEOCAT_CONFIG_DIR/backup.env"
}

restic_run() {
  docker run --rm -i --network none --read-only --cap-drop ALL --tmpfs /tmp \
    --user "$(id -u):$(id -g)" --hostname neocatecumenal \
    -e RESTIC_REPOSITORY -e RESTIC_PASSWORD_FILE=/run/secrets/restic_password -e RESTIC_CACHE_DIR=/tmp/restic-cache \
    -v "$RESTIC_PASSWORD_FILE:/run/secrets/restic_password:ro" -v "$NEOCAT_BACKUP_DIR:$NEOCAT_BACKUP_DIR" "$restic_image" "$@"
}

psql_in() {
  local database="$1"
  shift
  compose exec -T postgres sh -c \
    'user="$(cat /run/secrets/database_username)"; exec psql -U "$user" -d "$0" -v ON_ERROR_STOP=1 -qAt "$@"' \
    "$database" "$@"
}

sql() {
  psql_in "${2:-neocatecumenal}" -c "$1"
}

start_postgres() {
  compose up -d --wait --wait-timeout 120 postgres >/dev/null 2>&1
}

load_schema() {
  local migration
  for migration in "$root"/backend/src/main/resources/db/migration/V*.sql; do
    psql_in neocatecumenal <"$migration" >/dev/null || return 1
  done
}

days_ago() {
  date -u -v-"$1"d '+%Y-%m-%d %H:%M:%S' 2>/dev/null || date -u -d "$1 days ago" '+%Y-%m-%d %H:%M:%S'
}

add_synthetic_snapshot() {
  printf 'synthetic' | restic_run backup --stdin --stdin-filename neocatecumenal.dump --host neocatecumenal \
    --time "$(days_ago "$1")" >/dev/null 2>&1
}

init_repository() {
  restic_run init >/dev/null 2>&1
}

snapshot_ages() {
  restic_run snapshots --json 2>/dev/null | python3 -I -c '
import datetime, json, re, sys
now = datetime.datetime.now(datetime.timezone.utc)
def parse(text):
    return datetime.datetime.fromisoformat(re.sub(r"(\.\d{6})\d+", r"\1", text))
ages = sorted(round((now - parse(s["time"])).total_seconds() / 86400) for s in json.load(sys.stdin))
print(" ".join(str(a) for a in ages))'
}

ping_log() {
  cat "$pings"
}

reset_pings() {
  : >"$pings"
}
