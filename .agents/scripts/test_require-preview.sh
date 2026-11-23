#!/bin/bash
script="$(cd "$(dirname "$0")" && pwd)/require-preview.py"
fail=0

run() {
  jq -n --arg c "$1" '{tool_name:"Bash",tool_input:{command:$c}}' | python3 -I "$script"
}

expect() {
  local label="$1" command="$2" want="$3" out got
  out=$(run "$command")
  if [ -z "$out" ]; then
    got="pass"
  else
    got=$(jq -r '.hookSpecificOutput.permissionDecision' <<<"$out")
  fi
  if [ "$got" = "$want" ]; then
    echo "ok   $label"
  else
    echo "FAIL $label: expected $want, got $got"
    fail=1
  fi
}

expect "commit without preview" 'git commit -m "add room"' deny
expect "push without preview" 'git push origin feat/x' deny
expect "pr create without preview" 'gh pr create --head feat/x' deny
expect "pr merge without preview" 'gh pr merge 3 --merge' deny
expect "chained commit and push" 'git add . && git commit -m x && git push' deny
expect "git -C commit" 'git -C app commit -m x' deny
expect "multiline commit" $'git commit -m "x" \\\n  --no-verify' deny
expect "empty preview" 'git commit -m x # preview:' deny
expect "commit with preview" 'git commit -m x # preview: guarda los cambios de reservas' ask
expect "push with preview" 'git push # preview: sube la rama' ask
expect "pr create with preview" 'gh pr create --head x # preview: abre la PR' ask
expect "git status" 'git status' pass
expect "git diff" 'git diff --stat' pass
expect "git log" 'git log --oneline -5' pass
expect "gh pr view" 'gh pr view 3' pass
expect "ls" 'ls -la' pass

out=$(printf 'not json' | python3 -I "$script")
if [ "$(jq -r '.hookSpecificOutput.permissionDecision' <<<"$out")" = "deny" ]; then
  echo "ok   invalid input fails closed"
else
  echo "FAIL invalid input fails closed"
  fail=1
fi

exit $fail
