import json
import re
import sys

ACTION = re.compile(
    r"(?:^|[;&|(]\s*|\bthen\s+|\bdo\s+)"
    r"(?:git(?:\s+(?:-[Cc]\s+\S+|--\S+))*\s+(?:commit|push)|gh\s+pr\s+(?:create|merge))\b"
)
PREVIEW = re.compile(r"#\s*preview:[ \t]*(\S[^\n]*)")


def respond(decision, reason):
    print(json.dumps({
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": decision,
            "permissionDecisionReason": reason,
        }
    }))


def main():
    try:
        payload = json.load(sys.stdin)
        command = payload["tool_input"].get("command") or ""
        if not isinstance(command, str):
            raise TypeError
    except (ValueError, KeyError, TypeError, AttributeError):
        respond("deny", "Hook require-preview: no se ha podido leer la llamada; bloqueada por seguridad.")
        return

    flat = command.replace("\\\n", "").replace("\n", " ")
    if not ACTION.search(flat):
        return

    match = PREVIEW.search(command)
    if not match:
        respond(
            "deny",
            "Hook require-preview: git commit, git push y gh pr create|merge necesitan vista previa. "
            "Muestra en el chat qué cambia y reintenta el mismo comando añadiendo al final "
            "'# preview: <una línea>'.",
        )
        return

    respond("ask", "Claude quiere: " + match.group(1).strip()[:300] + ". Aprueba solo si coincide con la vista previa del chat.")


main()
