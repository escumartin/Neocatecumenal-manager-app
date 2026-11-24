import hashlib
import json
import os
import subprocess
import sys

TAIL_LINES = 30
GATE_TIMEOUT_SECONDS = 840

MODULES = {
    "backend": {
        "paths": ["backend"],
        "cwd": "backend",
        "env": "RUN_GATES_BACKEND_CMD",
        "command": "./mvnw -B -q verify",
    },
    "app": {
        "paths": ["app", "api"],
        "cwd": "app",
        "env": "RUN_GATES_APP_CMD",
        "command": "npm run lint && npm run typecheck",
    },
}


def git(root, *args):
    return subprocess.run(["git", "-C", root, *args], capture_output=True, text=True, check=True).stdout


def changed_paths(root, paths):
    out = git(root, "status", "--porcelain=v1", "-z", "-uall", "--", *paths)
    entries = out.split("\0")
    found = []
    index = 0
    while index < len(entries):
        entry = entries[index]
        index += 1
        if len(entry) < 4:
            continue
        found.append(entry[3:])
        if entry[0] in "RC":
            index += 1
    return sorted(set(found))


def state_hash(root, paths):
    digest = hashlib.sha256()
    digest.update(git(root, "rev-parse", "HEAD").strip().encode())
    for path in paths:
        digest.update(b"\0" + path.encode() + b"\0")
        full = os.path.join(root, path)
        if os.path.isfile(full):
            with open(full, "rb") as handle:
                digest.update(hashlib.sha256(handle.read()).digest())
        else:
            digest.update(b"absent")
    return digest.hexdigest()


def cache_file(root):
    path = git(root, "rev-parse", "--git-path", "run-gates-cache").strip()
    return path if os.path.isabs(path) else os.path.join(root, path)


def load_cache(path):
    try:
        with open(path) as handle:
            data = json.load(handle)
        return data if isinstance(data, dict) else {}
    except (OSError, ValueError):
        return {}


def save_cache(path, cache):
    try:
        with open(path, "w") as handle:
            json.dump(cache, handle)
    except OSError:
        pass


def sdkman_java_home(root):
    try:
        with open(os.path.join(root, ".sdkmanrc")) as handle:
            for line in handle:
                key, _, value = line.strip().partition("=")
                if key == "java" and value:
                    candidate = os.path.expanduser(os.path.join("~/.sdkman/candidates/java", value))
                    if os.path.isdir(candidate):
                        return candidate
    except OSError:
        pass
    return None


def run_gate(root, module):
    command = os.environ.get(module["env"]) or module["command"]
    env = dict(os.environ, TESTCONTAINERS_RYUK_DISABLED="true")
    java_home = sdkman_java_home(root)
    if java_home:
        env["JAVA_HOME"] = java_home
    try:
        result = subprocess.run(
            command,
            shell=True,
            cwd=os.path.join(root, module["cwd"]),
            env=env,
            capture_output=True,
            text=True,
            timeout=GATE_TIMEOUT_SECONDS,
        )
    except subprocess.TimeoutExpired:
        return command, "timeout after %d s" % GATE_TIMEOUT_SECONDS
    except OSError as error:
        return command, str(error)
    if result.returncode == 0:
        return command, None
    lines = (result.stdout + result.stderr).strip().splitlines()
    return command, "\n".join(lines[-TAIL_LINES:])


def main():
    try:
        payload = json.load(sys.stdin)
    except ValueError:
        payload = {}
    if not isinstance(payload, dict):
        payload = {}
    if payload.get("stop_hook_active"):
        return

    root = os.environ.get("CLAUDE_PROJECT_DIR") or payload.get("cwd") or os.getcwd()
    try:
        root = git(root, "rev-parse", "--show-toplevel").strip()
        cache_path = cache_file(root)
        pending = {}
        for name, module in MODULES.items():
            paths = changed_paths(root, module["paths"])
            if paths:
                pending[name] = state_hash(root, paths)
    except (subprocess.CalledProcessError, OSError):
        return

    if not pending:
        return

    cache = load_cache(cache_path)
    failures = []
    for name, digest in pending.items():
        if cache.get(name) == digest:
            continue
        command, error = run_gate(root, MODULES[name])
        if error is None:
            cache[name] = digest
        else:
            cache.pop(name, None)
            failures.append("Gate en rojo en %s (cd %s && %s):\n%s" % (name, MODULES[name]["cwd"], command, error))
    save_cache(cache_path, cache)

    if failures:
        print(json.dumps({"decision": "block", "reason": "\n\n".join(failures)}))


main()
