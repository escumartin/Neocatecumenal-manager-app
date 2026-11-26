#!/usr/bin/env bash
set -uo pipefail

project="neocatecumenal-restore-test"
. "$(cd "$(dirname "$0")" && pwd)/backup-lib.sh"

backup="$deploy/bin/neocat-backup"
restore="$deploy/bin/neocat-restore"
report="$work/restore.report"

community_1="00000000-0000-0000-0000-000000000c01"
community_2="00000000-0000-0000-0000-000000000c02"
user_erased="00000000-0000-0000-0000-0000000000a1"
user_kept="00000000-0000-0000-0000-0000000000a2"
member_deleted="00000000-0000-0000-0000-0000000000d1"
member_withdrawn="00000000-0000-0000-0000-0000000000d2"
member_responsible="00000000-0000-0000-0000-0000000000d3"
member_linked="00000000-0000-0000-0000-0000000000d4"
member_old_erasure="00000000-0000-0000-0000-0000000000d5"
member_untouched="00000000-0000-0000-0000-0000000000d6"

run_backup() {
  "$backup" >"$output" 2>&1
}

run_restore() {
  "$restore" "$@" >"$report" 2>&1
}

restored() {
  sql "$1" "$2"
}

production_fingerprint() {
  sql "select md5(string_agg(t, '|' order by t)) from (
         select 'u:' || id || user_id from identity_erasure
         union all select 'm:' || id || member_id || coalesce(field, '') from community_erasure
         union all select 'c:' || id || coalesce(mobile, '') || given_name from community_member
         union all select 's:' || id from identity_session) rows"
}

seed_database() {
  sql "insert into identity_community (id, number, active) values ('$community_1', 1, true), ('$community_2', 2, true)" >/dev/null
  sql "insert into community (id, number, status, version) values ('$community_1', 1, 'ACTIVE', 0), ('$community_2', 2, 'ACTIVE', 0)" >/dev/null
  sql "insert into identity_user (id, access_id, name, role, community_id, password_hash, password_change_required, version) values
         ('$user_erased', 100001, 'Usuaria Borrada', 'REGISTERED', '$community_1', 'hash-ficticio', false, 0),
         ('$user_kept', 100002, 'Usuario Conservado', 'MODERATOR', '$community_1', 'hash-ficticio', false, 0)" >/dev/null
  sql "insert into identity_session (id, user_id, token_hash, created_at, last_used_at) values
         ('00000000-0000-0000-0000-0000000000e1', '$user_erased', decode('01', 'hex'), now(), now()),
         ('00000000-0000-0000-0000-0000000000e2', '$user_kept', decode('02', 'hex'), now(), now())" >/dev/null
  sql "insert into community_member (id, community_id, given_name, surnames, mobile, landline, email, user_id, version) values
         ('$member_deleted', '$community_1', 'Zenobia', 'Quirógrafo', '600000101', null, 'zenobia.q@example.test', null, 0),
         ('$member_withdrawn', '$community_1', 'Hermenegilda', 'Valdemoro', '600000102', null, 'hermenegilda.v@example.test', null, 0),
         ('$member_responsible', '$community_1', 'Anselmo', 'Piedrahita', '600000103', '900000103', null, null, 0),
         ('$member_linked', '$community_1', 'Casimira', 'Villamayor', null, null, null, '$user_erased', 0),
         ('$member_old_erasure', '$community_2', 'Eufrasio', 'Garrapata', '600000104', null, null, null, 0),
         ('$member_untouched', '$community_2', 'Telesforo', 'Almendral', '600000105', null, null, null, 0)" >/dev/null
  sql "update community set responsible_member_id = '$member_responsible' where id = '$community_1'" >/dev/null
  seed_spaces_and_agenda
  sql "insert into community_contact_consent (id, member_id, field, decision, channel, recorded_by_user_id, recorder_role, notice_given, recorded_at) values
         ('00000000-0000-0000-0000-0000000000f1', '$member_deleted', 'MOBILE', 'GRANTED', 'VERBAL', '$user_kept', 'MODERATOR', true, now())" >/dev/null
  sql "insert into community_erasure (id, member_id, field, erased_at) values
         ('00000000-0000-0000-0000-0000000000f9', '$member_old_erasure', null, now() - interval '1 day')" >/dev/null
}

seed_spaces_and_agenda() {
  local room="00000000-0000-0000-0000-000000000501"
  sql "insert into room (id, name, active, version, created_at) values ('$room', 'Salón ficticio', true, 0, now())" >/dev/null
  sql "insert into booking_request (id, room_id, community_id, requested_by, event_id, reason, first_date, last_date, start_time, end_time, status, version, created_at) values
         ('00000000-0000-0000-0000-000000000511', '$room', '$community_1', '$user_erased', null, 'Motivo ficticio', current_date + 10, current_date + 10, '18:00', '19:00', 'APPROVED', 0, now()),
         ('00000000-0000-0000-0000-000000000512', '$room', '$community_1', '$user_kept', null, 'Motivo ficticio', current_date + 11, current_date + 11, '18:00', '19:00', 'APPROVED', 0, now())" >/dev/null
  sql "insert into booking_status_change (booking_request_id, from_status, to_status, changed_by, changed_at) values
         ('00000000-0000-0000-0000-000000000511', 'PENDING', 'APPROVED', '$user_erased', now()),
         ('00000000-0000-0000-0000-000000000512', 'PENDING', 'APPROVED', '$user_kept', now())" >/dev/null
  sql "insert into event (id, community_id, type, title, event_date, start_time, end_time, room_id, booking_request_id, created_by, created_at) values
         ('00000000-0000-0000-0000-000000000521', '$community_1', 'WORD', 'Evento ficticio', current_date + 10, '18:00', '19:00', '$room', '00000000-0000-0000-0000-000000000511', '$user_erased', now()),
         ('00000000-0000-0000-0000-000000000522', '$community_1', 'WORD', 'Evento ficticio', current_date + 11, '18:00', '19:00', '$room', '00000000-0000-0000-0000-000000000512', '$user_kept', now())" >/dev/null
}

erase_after_backup() {
  sql "update community set responsible_member_id = null where id = '$community_1'" >/dev/null
  sql "delete from community_member where id in ('$member_deleted', '$member_responsible')" >/dev/null
  sql "update community_member set mobile = null where id = '$member_withdrawn'" >/dev/null
  sql "update community_member set user_id = null where id = '$member_linked'" >/dev/null
  sql "delete from identity_user where id = '$user_erased'" >/dev/null
  sql "insert into identity_erasure (id, user_id, erased_at) values
         ('00000000-0000-0000-0000-000000000a01', '$user_erased', now())" >/dev/null
  sql "insert into community_erasure (id, member_id, field, erased_at) values
         ('00000000-0000-0000-0000-000000000b01', '$member_deleted', null, now()),
         ('00000000-0000-0000-0000-000000000b02', '$member_withdrawn', 'MOBILE', now()),
         ('00000000-0000-0000-0000-000000000b03', '$member_responsible', null, now())" >/dev/null
}

first_snapshot() {
  restic_run snapshots --json 2>/dev/null | grep -o '"short_id":"[^"]*"' | head -n 1 | sed 's/.*:"\(.*\)"/\1/'
}

member_exists() {
  [ "$(restored "select count(*) from community_member where id = '$2'" "$1")" = "1" ]
}

database_exists() {
  [ "$(sql "select count(*) from pg_database where datname = '$1'" postgres)" = "1" ]
}

report_lists_ids_and_actions() {
  grep -q "usuario $user_erased: borrado" "$report" \
    && grep -q "usuario $user_erased: referencias anonimizadas: 3" "$report" \
    && grep -q "hermano $member_deleted: borrado" "$report" \
    && grep -q "hermano $member_withdrawn campo MOBILE: vaciado" "$report" \
    && grep -q "hermano $member_responsible: anonimizado" "$report" \
    && grep -q 'supresiones aplicadas: 4' "$report"
}

report_has_no_names() {
  ! grep -qiE 'zenobia|hermenegilda|anselmo|casimira|eufrasio|telesforo|quirógrafo|valdemoro|piedrahita|villamayor|example\.test|60000010' "$report"
}

restored_database_has_the_erasures() {
  local db="$1"
  ! member_exists "$db" "$member_deleted" \
    && [ "$(restored "select count(*) from community_contact_consent where member_id = '$member_deleted'" "$db")" = "0" ] \
    && [ "$(restored "select coalesce(mobile, 'null') || '/' || email from community_member where id = '$member_withdrawn'" "$db")" = "null/hermenegilda.v@example.test" ] \
    && [ "$(restored "select given_name || '/' || coalesce(surnames, 'null') || '/' || coalesce(mobile, 'null') || '/' || coalesce(landline, 'null') || '/' || coalesce(email, 'null') from community_member where id = '$member_responsible'" "$db")" = "Suprimido/null/null/null/null" ] \
    && [ "$(restored "select count(*) from identity_user where id = '$user_erased'" "$db")" = "0" ] \
    && [ "$(restored "select count(*) from identity_user where id = '$user_kept'" "$db")" = "1" ] \
    && [ "$(restored "select coalesce(user_id::text, 'null') from community_member where id = '$member_linked'" "$db")" = "null" ] \
    && [ "$(restored "select given_name from community_member where id = '$member_old_erasure'" "$db")" = "Eufrasio" ] \
    && [ "$(restored "select given_name from community_member where id = '$member_untouched'" "$db")" = "Telesforo" ]
}

restored_database_anonymizes_spaces_and_agenda() {
  local db="$1"
  [ "$(restored "select coalesce(requested_by::text, 'null') from booking_request where id = '00000000-0000-0000-0000-000000000511'" "$db")" = "null" ] \
    && [ "$(restored "select coalesce(changed_by::text, 'null') from booking_status_change where booking_request_id = '00000000-0000-0000-0000-000000000511'" "$db")" = "null" ] \
    && [ "$(restored "select coalesce(created_by::text, 'null') from event where id = '00000000-0000-0000-0000-000000000521'" "$db")" = "null" ] \
    && [ "$(restored "select requested_by from booking_request where id = '00000000-0000-0000-0000-000000000512'" "$db")" = "$user_kept" ] \
    && [ "$(restored "select changed_by from booking_status_change where booking_request_id = '00000000-0000-0000-0000-000000000512'" "$db")" = "$user_kept" ] \
    && [ "$(restored "select created_by from event where id = '00000000-0000-0000-0000-000000000522'" "$db")" = "$user_kept" ]
}

restored_database_has_no_sessions() {
  [ "$(restored "select count(*) from identity_session" "$1")" = "0" ]
}

restored_database_keeps_the_applied_rows() {
  [ "$(restored "select count(*) from identity_erasure where id = '00000000-0000-0000-0000-000000000a01'" "$1")" = "1" ] \
    && [ "$(restored "select count(*) from community_erasure" "$1")" = "4" ]
}

restore_from_production_source() {
  local before after
  before="$(production_fingerprint)"
  run_restore "$snapshot_one" neocat_restore_one || return 1
  after="$(production_fingerprint)"
  [ "$before" = "$after" ] && grep -q 'fuente de supresiones: producción' "$report"
}

restore_reports_elapsed_time() {
  grep -qE '^tiempo de restauración: [0-9]+ s$' "$report"
}

refuses_a_database_with_data() {
  ! run_restore "$snapshot_one" neocat_restore_one \
    && ! run_restore latest neocatecumenal \
    && [ "$(sql "select count(*) from community_member where id = '$member_deleted'")" = "0" ]
}

rejects_invalid_database_names() {
  ! run_restore latest 'x"; drop database neocatecumenal; --' && database_exists neocatecumenal
}

refuses_an_unmounted_partition() {
  ! NEOCAT_BACKUP_REQUIRE_MOUNT=true run_restore latest neocat_restore_unmounted \
    && ! database_exists neocat_restore_unmounted
}

fails_for_an_unknown_snapshot() {
  ! run_restore ffffffff neocat_restore_unknown && ! database_exists neocat_restore_unknown
}

restore_from_latest_snapshot_source() {
  run_backup || return 1
  sql "drop database neocatecumenal with (force)" postgres >/dev/null || return 1
  run_restore "$snapshot_one" neocat_restore_two && grep -q 'fuente de supresiones: copia más reciente' "$report"
}

recovery_into_the_empty_production_database() {
  run_restore latest neocatecumenal && database_exists neocatecumenal \
    && ! member_exists neocatecumenal "$member_deleted" \
    && ! member_exists neocatecumenal "$member_responsible" \
    && member_exists neocatecumenal "$member_untouched" \
    && [ "$(restored "select count(*) from community_member where id = '$member_withdrawn' and mobile is null" neocatecumenal)" = "1" ] \
    && [ "$(restored "select count(*) from identity_user where id = '$user_erased'" neocatecumenal)" = "0" ] \
    && restored_database_has_no_sessions neocatecumenal
}

no_temporary_database_is_left() {
  [ "$(sql "select count(*) from pg_database where datname like '%source%'" postgres)" = "0" ]
}

restore_never_prints_secrets() {
  ! grep -qF -e "$restic_secret" -e "$database_password" "$report" "$output"
}

apply_sql_and_restore_have_no_comments() {
  ! grep -nE '^\s*#[^!]' "$restore" \
    && ! grep -nE '^\s*--(\s|$)' "$deploy/sql/apply-erasures.sql"
}

if [ ! -x "$restore" ] || [ ! -f "$deploy/sql/apply-erasures.sql" ]; then
  echo "FALLO: faltan deploy/bin/neocat-restore o deploy/sql/apply-erasures.sql"
  exit 1
fi

start_ping_stub
use_repository restore
write_backup_env
check "el stack de PostgreSQL arranca sano" start_postgres
check "el esquema real de las migraciones se carga" load_schema
check "los datos sintéticos se cargan" seed_database
check "la copia previa a las supresiones termina bien" run_backup
snapshot_one="$(first_snapshot)"
check "la copia previa tiene instantánea" test -n "$snapshot_one"
check "las supresiones posteriores se registran en producción" erase_after_backup

check "restaurar usa la base de producción como fuente y no la modifica" restore_from_production_source
check "el informe lista ids y acciones" report_lists_ids_and_actions
check "el informe nunca muestra nombres ni contacto" report_has_no_names
check "la base restaurada reaplica usuario, hermano con cascada, campo de contacto y anonimización" restored_database_has_the_erasures neocat_restore_one
check "la base restaurada anonimiza creador de evento, solicitante y autor de cambios de reserva" restored_database_anonymizes_spaces_and_agenda neocat_restore_one
check "las filas anteriores a la copia no se reaplican" member_exists neocat_restore_one "$member_old_erasure"
check "identity_session queda vacía en la base restaurada" restored_database_has_no_sessions neocat_restore_one
check "las filas aplicadas se copian a la base restaurada" restored_database_keeps_the_applied_rows neocat_restore_one
check "el informe indica el tiempo de restauración" restore_reports_elapsed_time
check "una base de destino con datos se rechaza" refuses_a_database_with_data
check "un nombre de base inválido se rechaza sin tocar producción" rejects_invalid_database_names
check "con la partición sin montar se rechaza sin crear la base" refuses_an_unmounted_partition
check "una instantánea desconocida falla sin crear la base" fails_for_an_unknown_snapshot
check "sin producción, la fuente es la copia más reciente" restore_from_latest_snapshot_source
check "la restauración desde la copia más reciente reaplica las mismas supresiones" restored_database_has_the_erasures neocat_restore_two
check "la restauración desde la copia más reciente anonimiza agenda y salones" restored_database_anonymizes_spaces_and_agenda neocat_restore_two
check "con el volumen de datos vacío, restaurar latest en la base de producción la deja en servicio" recovery_into_the_empty_production_database
check "no quedan bases temporales" no_temporary_database_is_left
check "la salida nunca muestra secretos" restore_never_prints_secrets
check "neocat-restore y apply-erasures.sql no llevan comentarios" apply_sql_and_restore_have_no_comments

finish
