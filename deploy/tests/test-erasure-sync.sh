#!/usr/bin/env bash
set -uo pipefail

project="neocatecumenal-sync-test"
. "$(cd "$(dirname "$0")" && pwd)/backup-lib.sh"

sync="$deploy/bin/neocat-erasure-sync"
calls="$work/backup-calls"
backup_stub="$work/backup-stub"
failing_stub="$work/failing-stub"

printf '#!/usr/bin/env bash\necho called >>"%s"\n' "$calls" >"$backup_stub"
printf '#!/usr/bin/env bash\nexit 1\n' >"$failing_stub"
chmod +x "$backup_stub" "$failing_stub"

run_sync() {
  NEOCAT_BACKUP_COMMAND="${NEOCAT_BACKUP_COMMAND:-$backup_stub}" "$sync" >"$output" 2>&1
}

reset_state() {
  : >"$calls"
  reset_pings
  sql "delete from identity_erasure; delete from community_erasure" >/dev/null
}

call_count() {
  wc -l <"$calls" | tr -d ' '
}

without_rows_it_does_not_back_up_and_pings() {
  reset_state
  run_sync && [ "$(call_count)" = "0" ] && grep -qx 'POST /erasure-token|sincronización correcta' "$pings"
}

rows_before_the_last_snapshot_do_not_trigger_a_backup() {
  reset_state
  sql "insert into identity_erasure (id, user_id, erased_at) values (gen_random_uuid(), gen_random_uuid(), now() - interval '1 day')" >/dev/null
  sql "insert into community_erasure (id, member_id, field, erased_at) values (gen_random_uuid(), gen_random_uuid(), null, now() - interval '1 day')" >/dev/null
  run_sync && [ "$(call_count)" = "0" ]
}

an_identity_row_after_the_last_snapshot_triggers_a_backup() {
  reset_state
  sleep 1
  sql "insert into identity_erasure (id, user_id, erased_at) values (gen_random_uuid(), gen_random_uuid(), now())" >/dev/null
  run_sync && [ "$(call_count)" = "1" ] && grep -qx 'POST /erasure-token|sincronización correcta' "$pings"
}

a_community_row_after_the_last_snapshot_triggers_a_backup() {
  reset_state
  sql "insert into community_erasure (id, member_id, field, erased_at) values (gen_random_uuid(), gen_random_uuid(), 'EMAIL', now())" >/dev/null
  run_sync && [ "$(call_count)" = "1" ]
}

a_failing_backup_pings_fail_with_fixed_text() {
  reset_state
  sql "insert into community_erasure (id, member_id, field, erased_at) values (gen_random_uuid(), gen_random_uuid(), null, now())" >/dev/null
  ! NEOCAT_BACKUP_COMMAND="$failing_stub" run_sync \
    && grep -qx 'POST /erasure-token/fail|fallo de copia por supresiones' "$pings"
}

without_snapshots_any_row_triggers_a_backup() {
  use_repository empty
  write_backup_env
  init_repository
  reset_state
  sql "insert into identity_erasure (id, user_id, erased_at) values (gen_random_uuid(), gen_random_uuid(), now() - interval '2 days')" >/dev/null
  run_sync && [ "$(call_count)" = "1" ]
}

unmounted_partition_fails_without_writing() {
  use_repository unmounted
  write_backup_env
  reset_state
  ! NEOCAT_BACKUP_REQUIRE_MOUNT=true run_sync \
    && [ -z "$(ls -A "$NEOCAT_BACKUP_DIR")" ] \
    && [ "$(call_count)" = "0" ] \
    && grep -qx 'POST /erasure-token/fail|partición de copias sin montar' "$pings"
}

output_never_shows_secrets() {
  ! grep -rqF -e "$restic_secret" -e "$database_password" "$output" "$pings"
}

units_and_script_are_valid() {
  ! grep -nE '^\s*#[^!]' "$deploy/systemd/neocat-erasure-sync.service" "$deploy/systemd/neocat-erasure-sync.timer" "$sync" \
    && grep -q '^OnCalendar=hourly$' "$deploy/systemd/neocat-erasure-sync.timer" \
    && grep -q '^ExecStart=/usr/local/bin/neocat-erasure-sync$' "$deploy/systemd/neocat-erasure-sync.service" \
    && grep -q '^Type=oneshot$' "$deploy/systemd/neocat-erasure-sync.service" \
    && grep -q '^NEOCAT_PING_ERASURE_SYNC=$' "$deploy/env/backup.env.example"
}

if [ ! -x "$sync" ]; then
  echo "FALLO: falta deploy/bin/neocat-erasure-sync ejecutable"
  exit 1
fi

start_ping_stub
use_repository sync
write_backup_env
check "el stack de PostgreSQL arranca sano" start_postgres
check "el esquema real de las migraciones se carga" load_schema
check "el repositorio recibe una instantánea inicial" init_repository
check "hay una instantánea inicial" add_synthetic_snapshot 0

check "sin filas de supresión no lanza copia y hace ping de éxito" without_rows_it_does_not_back_up_and_pings
check "filas anteriores a la última instantánea no lanzan copia" rows_before_the_last_snapshot_do_not_trigger_a_backup
check "una fila de identity_erasure posterior a la última instantánea lanza neocat-backup" an_identity_row_after_the_last_snapshot_triggers_a_backup
check "una fila de community_erasure posterior a la última instantánea lanza neocat-backup" a_community_row_after_the_last_snapshot_triggers_a_backup
check "si la copia falla sale con ≠ 0 y hace ping /fail con texto fijo" a_failing_backup_pings_fail_with_fixed_text
check "sin instantáneas cualquier fila lanza la copia" without_snapshots_any_row_triggers_a_backup
check "sin partición montada falla sin escribir y hace ping /fail" unmounted_partition_fails_without_writing
check "la salida y los pings nunca muestran secretos" output_never_shows_secrets
check "neocat-erasure-sync.service corre aislado con ProtectSystem=strict, ProtectHome, PrivateTmp, NoNewPrivileges y escritura mínima" unit_is_sandboxed "$deploy/systemd/neocat-erasure-sync.service" "-/mnt/neocatecumenal-backups"
check "systemd planifica neocat-erasure-sync cada hora y los ficheros no llevan comentarios" units_and_script_are_valid

finish
