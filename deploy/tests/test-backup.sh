#!/usr/bin/env bash
set -uo pipefail

project="neocatecumenal-backup-test"
. "$(cd "$(dirname "$0")" && pwd)/backup-lib.sh"

backup="$deploy/bin/neocat-backup"
fixed_failure_texts="fallo de copia|partición de copias sin montar|partición de copias casi llena|instantánea de copia demasiado antigua|comprobación de integridad fallida"

run_backup() {
  "$backup" >"$output" 2>&1
}

dump_contents() {
  restic_run dump latest /neocatecumenal.dump 2>/dev/null \
    | compose exec -T postgres sh -c 'exec pg_restore -l' 2>/dev/null
}

script_exists() {
  [ -x "$backup" ]
}

backup_writes_todays_snapshot() {
  use_repository today
  write_backup_env
  reset_pings
  run_backup && [ "$(snapshot_ages)" = "0" ]
}

backup_pings_start_and_success_without_output() {
  grep -qx 'POST /backup-token/start|' "$pings" \
    && grep -q '^POST /backup-token|' "$pings" \
    && ! grep -q 'fail' "$pings"
}

pings_carry_only_fixed_text() {
  ! ping_log | grep -vE '^POST /backup-token(/start|/fail)?\|(copia correcta|('"$fixed_failure_texts"'))?$'
}

dump_excludes_sessions_and_throttles_but_keeps_data() {
  local toc
  toc="$(dump_contents)"
  printf '%s' "$toc" | grep -q 'TABLE DATA public identity_user' \
    && ! printf '%s' "$toc" | grep -q 'TABLE DATA public identity_session' \
    && ! printf '%s' "$toc" | grep -q 'TABLE DATA public identity_login_ip_throttle' \
    && ! printf '%s' "$toc" | grep -q 'TABLE DATA public identity_login_pair_throttle'
}

retention_keeps_recent_snapshots_and_drops_old_ones() {
  use_repository retention
  write_backup_env
  init_repository
  for age in 10 29 31 40; do
    add_synthetic_snapshot "$age"
  done
  [ "$(snapshot_ages)" = "10 29 31 40" ] || return 1
  run_backup && [ "$(snapshot_ages)" = "0 10 29" ]
}

loose_retention_with_old_snapshot_fails() {
  use_repository loose
  write_backup_env
  init_repository
  add_synthetic_snapshot 40
  reset_pings
  ! NEOCAT_BACKUP_RETENTION=100d run_backup \
    && grep -q '^POST /backup-token/fail|instantánea de copia demasiado antigua$' "$pings"
}

full_partition_fails_with_fixed_text() {
  use_repository full
  write_backup_env
  reset_pings
  ! NEOCAT_BACKUP_MAX_USE=1 run_backup \
    && grep -q '^POST /backup-token/fail|partición de copias casi llena$' "$pings"
}

unmounted_partition_fails_without_writing() {
  use_repository unmounted
  write_backup_env
  reset_pings
  ! NEOCAT_BACKUP_REQUIRE_MOUNT=true run_backup \
    && [ -z "$(ls -A "$NEOCAT_BACKUP_DIR")" ] \
    && grep -q '^POST /backup-token/fail|partición de copias sin montar$' "$pings"
}

inaccessible_repository_fails() {
  use_repository inaccessible
  write_backup_env
  init_repository
  new_restic_password
  write_backup_env
  reset_pings
  ! run_backup && grep -q '^POST /backup-token/fail|fallo de copia$' "$pings"
}

integrity_check_runs_on_its_day() {
  use_repository sunday
  write_backup_env
  NEOCAT_BACKUP_CHECK_DAY="$(date +%u)" run_backup \
    && grep -q 'comprobación de integridad correcta' "$output"
}

integrity_check_is_skipped_other_days() {
  use_repository weekday
  write_backup_env
  NEOCAT_BACKUP_CHECK_DAY="$((($(date +%u) % 7) + 1))" run_backup \
    && ! grep -q 'comprobación de integridad' "$output"
}

failing_dump_leaves_no_new_snapshot() {
  use_repository nodatabase
  write_backup_env
  run_backup || return 1
  compose stop postgres >/dev/null 2>&1
  reset_pings
  ! run_backup \
    && [ "$(snapshot_ages)" = "0" ] \
    && [ "$(restic_run snapshots --json 2>/dev/null | grep -o '"time"' | wc -l | tr -d ' ')" = "1" ] \
    && grep -q '^POST /backup-token/fail|fallo de copia$' "$pings"
}

output_never_shows_secrets() {
  ! grep -rqF -e "$restic_secret" -e "$database_password" "$output" "$pings"
}

units_and_env_example_are_valid() {
  ! grep -nE '^\s*#[^!]' "$deploy/systemd/neocat-backup.service" "$deploy/systemd/neocat-backup.timer" \
      "$deploy/env/backup.env.example" "$backup" \
    && ! grep -qvE '^[A-Z_]+=$' "$deploy/env/backup.env.example" \
    && grep -q '^OnCalendar=\*-\*-\* 03:30:00$' "$deploy/systemd/neocat-backup.timer" \
    && grep -q '^ExecStart=/usr/local/bin/neocat-backup$' "$deploy/systemd/neocat-backup.service" \
    && grep -q '^Type=oneshot$' "$deploy/systemd/neocat-backup.service"
}

restic_password_travels_as_a_read_only_file() {
  local script
  for script in neocat-backup neocat-restore neocat-erasure-sync; do
    grep -q 'RESTIC_PASSWORD_FILE=/run/secrets/restic_password' "$deploy/bin/$script" \
      && grep -q ':/run/secrets/restic_password:ro' "$deploy/bin/$script" \
      && ! grep -qE '(^|[^_A-Z])RESTIC_PASSWORD($|[^_A-Z])' "$deploy/bin/$script" \
      || return 1
  done
}

password_file_is_private() {
  [ "$(stat -c '%a' "$RESTIC_PASSWORD_FILE" 2>/dev/null || stat -f '%Lp' "$RESTIC_PASSWORD_FILE")" = "400" ]
}

missing_password_file_fails_with_fixed_text() {
  use_repository nopassword
  write_backup_env
  reset_pings
  ! RESTIC_PASSWORD_FILE="$work/absent.password" run_backup \
    && grep -q '^POST /backup-token/fail|fallo de copia$' "$pings"
}

if ! script_exists; then
  echo "FALLO: falta deploy/bin/neocat-backup ejecutable"
  exit 1
fi

start_ping_stub
check "el stack de PostgreSQL arranca sano" start_postgres
check "el esquema real de las migraciones se carga" load_schema
sql "insert into identity_community (id, number, active) values ('00000000-0000-0000-0000-000000000001', 1, true)" >/dev/null
sql "insert into identity_user (id, access_id, name, role, community_id, password_hash, password_change_required, version) values ('00000000-0000-0000-0000-0000000000a1', 123456, 'Usuario Ficticio', 'MODERATOR', '00000000-0000-0000-0000-000000000001', 'hash-ficticio', false, 0)" >/dev/null
sql "insert into identity_session (id, user_id, token_hash, created_at, last_used_at) values ('00000000-0000-0000-0000-0000000000b1', '00000000-0000-0000-0000-0000000000a1', decode('0102', 'hex'), now(), now())" >/dev/null
sql "insert into identity_login_ip_throttle (ip, recent_failures) values ('10.0.0.1', array[now()])" >/dev/null
sql "insert into identity_login_pair_throttle (access_id, ip, consecutive_failures, last_failure_at) values (123456, '10.0.0.1', 1, now())" >/dev/null

check "la copia deja la instantánea de hoy en el repositorio de la partición" backup_writes_todays_snapshot
check "la copia hace ping de inicio y de éxito sin salida de comandos" backup_pings_start_and_success_without_output
check "el volcado excluye sesiones y frenos de acceso y conserva los datos" dump_excludes_sessions_and_throttles_but_keeps_data
check "con instantáneas de 10, 29, 31 y 40 días quedan las de 10, 29 y la de hoy" retention_keeps_recent_snapshots_and_drops_old_ones
check "con retención desactivada y una instantánea de 40 días sale con ≠ 0 y hace ping /fail" loose_retention_with_old_snapshot_fails
check "con NEOCAT_BACKUP_MAX_USE=1 sale con ≠ 0 y hace ping /fail «partición de copias casi llena»" full_partition_fails_with_fixed_text
check "sin partición montada falla sin escribir en el disco de datos y hace ping /fail" unmounted_partition_fails_without_writing
check "un repositorio inaccesible sale con ≠ 0 y hace ping /fail" inaccessible_repository_fails
check "restic check se ejecuta el día configurado" integrity_check_runs_on_its_day
check "restic check no se ejecuta otros días" integrity_check_is_skipped_other_days
check "un volcado fallido no deja una instantánea vacía y hace ping /fail" failing_dump_leaves_no_new_snapshot
check "la contraseña de restic viaja como fichero montado de solo lectura y no por entorno" restic_password_travels_as_a_read_only_file
check "el fichero de contraseña de las pruebas es 0400" password_file_is_private
check "sin fichero de contraseña la copia falla con texto fijo" missing_password_file_fails_with_fixed_text
check "los pings llevan solo texto fijo" pings_carry_only_fixed_text
check "la salida y los pings nunca muestran secretos" output_never_shows_secrets
check "neocat-backup.service corre aislado con ProtectSystem=strict, ProtectHome, PrivateTmp, NoNewPrivileges y escritura mínima" unit_is_sandboxed "$deploy/systemd/neocat-backup.service" "-/mnt/neocatecumenal-backups"
check "systemd planifica neocat-backup a las 03:30 y los ficheros no llevan comentarios ni valores" units_and_env_example_are_valid

finish
