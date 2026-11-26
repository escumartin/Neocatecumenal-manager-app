#!/usr/bin/env bash
set -uo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"
. "$here/lib.sh"

image="neocatecumenal-backend:test-image"
revision="abc1234"

build_with() {
  docker build --build-context "api=$root/api" "$root/backend" "$@"
}

build_is_rejected_naming_the_revision() {
  local output
  if output="$(build_with "$@" 2>&1)"; then
    return 1
  fi
  grep -q "SERVICE_REVISION" <<<"$output"
}

build_without_revision() {
  build_is_rejected_naming_the_revision -t "$image-norev"
}

build_with_invalid_revision() {
  build_is_rejected_naming_the_revision -t "$image-badrev" --build-arg SERVICE_REVISION=NOT-A-COMMIT
}

build_with_short_revision() {
  build_is_rejected_naming_the_revision -t "$image-shortrev" --build-arg SERVICE_REVISION=abc12
}

user_is_unprivileged() {
  local user
  user="$(docker inspect -f '{{.Config.User}}' "$image")"
  [ -n "$user" ] && [ "$user" != "root" ] && [ "$user" != "0" ] && [[ "$user" != 0:* ]]
}

effective_uid_is_unprivileged() {
  [ "$(docker run --rm --entrypoint id "$image" -u)" != "0" ]
}

has_revision_label() {
  [ "$(docker inspect -f '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$image")" = "$revision" ]
}

has_revision_env() {
  docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$image" | grep -qx "SERVICE_REVISION=$revision"
}

has_healthcheck() {
  docker inspect -f '{{.Config.Healthcheck.Test}}' "$image" | grep -q "actuator/health/readiness"
}

has_no_local_profile_file() {
  [ -z "$(docker run --rm --entrypoint sh "$image" -c 'find / -name "application-local.yml" -not -path "/proc/*" 2>/dev/null')" ]
}

has_no_secret_files() {
  [ -z "$(docker run --rm --entrypoint sh "$image" -c 'find / \( -name ".env" -o -name "*.env" -o -name "*.pem" -o -name "*.key" \) -not -path "/proc/*" -not -path "/sys/*" -not -path "/usr/lib/*" -not -path "/etc/ssl/*" 2>/dev/null')" ]
}

has_no_secret_environment() {
  ! docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$image" | grep -Eiq 'password|secret|token|DATABASE_URL'
}

check "el build falla sin SERVICE_REVISION y lo nombra" build_without_revision
check "el build falla con una revisión que no es un commit y lo nombra" build_with_invalid_revision
check "el build falla con una revisión de menos de 7 caracteres y lo nombra" build_with_short_revision

if build_with -t "$image" --build-arg "SERVICE_REVISION=$revision" >/dev/null 2>&1; then
  echo "ok: el build con una revisión válida funciona"
  check "la imagen declara un usuario sin privilegios" user_is_unprivileged
  check "el proceso de la imagen corre sin privilegios" effective_uid_is_unprivileged
  check "la imagen trae la etiqueta OCI revision" has_revision_label
  check "la imagen trae ENV SERVICE_REVISION" has_revision_env
  check "la imagen trae healthcheck a readiness" has_healthcheck
  check "la imagen no contiene application-local.yml" has_no_local_profile_file
  check "la imagen no contiene ficheros de secretos" has_no_secret_files
  check "la imagen no lleva secretos en su entorno" has_no_secret_environment
else
  echo "FALLO: el build con una revisión válida funciona"
  failures=$((failures + 1))
fi

finish
