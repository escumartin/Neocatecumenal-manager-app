import json
import re
import sys

ACTION = re.compile(
    r"(?:^|[;&|(]\s*|\bthen\s+|\bdo\s+)"
    r"(?:\w+=\S*\s+)*"
    r"(?:git(?:\s+(?:-[Cc]\s+\S+|--\S+))*\s+(?:commit|push)|gh\s+pr\s+(?:create|merge))\b"
)
PREVIEW = re.compile(r"#\s*preview:[ \t]*(\S[^\n]*)")
FORBIDDEN = re.compile(
    r"\bgit\b[^;&|\n]*\bpush\b[^;&|\n]*(?:\s--force(?:-with-lease)?\b|\s-f\b|\s\+\S)"
    r"|\bgit\b[^;&|\n]*\bcommit\b[^;&|\n]*\s--amend\b"
)
SEGMENT_SPLIT = re.compile(r"&&|\|\||[;|\n]")
SAFE_SEGMENT = re.compile(r"^(?:\w+=\S*\s+)*(?:git|gh|cd|export)\b")


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

    if FORBIDDEN.search(flat):
        respond("deny", "Hook require-preview: force push y commit --amend no se aprueban nunca desde el asistente.")
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

    reason = "Vista previa: " + match.group(1).strip()[:300]
    segments = [part.strip() for part in SEGMENT_SPLIT.split(flat[:flat.find("#", PREVIEW.search(flat).start())]) if part.strip()]
    if all(SAFE_SEGMENT.match(part) for part in segments):
        respond("allow", reason)
    else:
        respond("ask", reason + ". El comando incluye pasos que no son git ni gh.")


main()
