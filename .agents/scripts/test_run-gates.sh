#!/bin/bash
script="$(cd "$(dirname "$0")" && pwd)/run-gates.py"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
fail=0

repo="$work/repo"
mkdir -p "$repo/backend" "$repo/app" "$repo/api"
git -C "$repo" init -q
git -C "$repo" config user.email t@example.com
git -C "$repo" config user.name t
echo base >"$repo/backend/a.txt"
echo base >"$repo/app/a.txt"
echo base >"$repo/api/a.txt"
git -C "$repo" add -A
git -C "$repo" commit -q -m base

counter="$work/runs"
export RUN_GATES_BACKEND_CMD="echo b >>'$counter'"
export RUN_GATES_APP_CMD="echo a >>'$counter'"
export CLAUDE_PROJECT_DIR="$repo"

run() {
  printf '%s' "${1:-{\}}" | python3 -I "$script"
}

check() {
  local label="$1" cond="$2"
  if eval "$cond"; then
    echo "ok   $label"
  else
    echo "FAIL $label"
    fail=1
  fi
}

runs() { [ -f "$counter" ] && wc -l <"$counter" | tr -d ' ' || echo 0; }

out=$(run)
check "no changes: silent" '[ -z "$out" ]'
check "no changes: no gate run" '[ "$(runs)" = 0 ]'

echo change >>"$repo/backend/a.txt"
out=$(run '{"stop_hook_active":true}')
check "stop_hook_active: silent" '[ -z "$out" ]'
check "stop_hook_active: no gate run" '[ "$(runs)" = 0 ]'

out=$(run)
check "backend change, green: silent" '[ -z "$out" ]'
check "backend change: only backend gate ran" '[ "$(runs)" = 1 ] && grep -q b "$counter"'

out=$(run)
check "valid cache: silent" '[ -z "$out" ]'
check "valid cache: gate not repeated" '[ "$(runs)" = 1 ]'

echo more >>"$repo/backend/a.txt"
out=$(run)
check "changed state: gate repeated" '[ "$(runs)" = 2 ]'

echo new >"$repo/app/new.txt"
out=$(run)
check "untracked app file: app gate ran" '[ "$(runs)" = 3 ] && grep -q a "$counter"'

echo x >>"$repo/api/a.txt"
out=$(run)
check "api change: app gate ran again" '[ "$(runs)" = 4 ]'

export RUN_GATES_BACKEND_CMD="seq 1 50; echo boom >&2; exit 3"
echo again >>"$repo/backend/a.txt"
out=$(run)
check "failure: decision block" '[ "$(jq -r .decision <<<"$out")" = block ]'
check "failure: reason names module and command" 'jq -r .reason <<<"$out" | grep -q "backend (cd backend && seq 1 50"'
check "failure: reason has error line" 'jq -r .reason <<<"$out" | grep -q boom'
check "failure: reason keeps only last 30 lines" '[ "$(jq -r .reason <<<"$out" | grep -c "^[0-9]*$")" = 29 ]'

out=$(run)
check "failure is not cached" '[ "$(jq -r .decision <<<"$out")" = block ]'

out=$(run '{"stop_hook_active":true}')
check "failure with stop_hook_active: no block" '[ -z "$out" ]'

exit $fail
