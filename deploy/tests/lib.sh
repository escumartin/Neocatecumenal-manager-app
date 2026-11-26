#!/usr/bin/env bash

failures=0

check() {
  local description="$1"
  shift
  if "$@" >/dev/null 2>&1; then
    echo "ok: $description"
  else
    echo "FALLO: $description"
    failures=$((failures + 1))
  fi
}

check_fails() {
  local description="$1"
  shift
  if "$@" >/dev/null 2>&1; then
    echo "FALLO: $description"
    failures=$((failures + 1))
  else
    echo "ok: $description"
  fi
}

create_database_secrets() {
  local home="$1" password="$2"
  NEOCAT_SECRETS_GID="$(id -g)"
  export NEOCAT_SECRETS_GID
  mkdir -p "$home/secrets"
  chmod 700 "$home"
  chmod 750 "$home/secrets"
  printf '%s' "neocatecumenal" >"$home/secrets/spring.datasource.username"
  printf '%s' "$password" >"$home/secrets/spring.datasource.password"
  chmod 440 "$home"/secrets/*
}

unit_is_sandboxed() {
  local unit="$1" writable="$2"
  grep -q '^ProtectSystem=strict$' "$unit" \
    && grep -q '^ProtectHome=yes$' "$unit" \
    && grep -q '^PrivateTmp=yes$' "$unit" \
    && grep -q '^NoNewPrivileges=yes$' "$unit" \
    && grep -q '^Environment=DOCKER_CONFIG=/etc/neocatecumenal/docker$' "$unit" \
    && [ "$(grep -c '^ReadWritePaths=' "$unit")" = "1" ] \
    && grep -q "^ReadWritePaths=$writable\$" "$unit"
}

finish() {
  if [ "$failures" -ne 0 ]; then
    echo "$failures comprobaciones fallidas" >&2
    exit 1
  fi
  echo "todas las comprobaciones correctas"
}
